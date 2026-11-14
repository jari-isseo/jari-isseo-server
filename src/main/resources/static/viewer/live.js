// =====================================================
//  자리있어 손님 화면 실시간 반영 (#20)
//  API 정의서 5장: GET /api/public/stores/{publicId}/events (SSE)
//
//  | event    | data                              | 화면 처리                           |
//  | snapshot | { statuses, lastUpdatedAt }       | 상태 전체 교체                       |
//  | status   | { tableId, occupied, updatedAt }  | 그 테이블 색만 변경                  |
//  | layout   | { version }                       | 스냅샷 API 를 다시 불러 전체 다시 그리기 |
//  | store    | { name, open }                    | 매장 이름·영업 상태 갱신              |
//
//  연결(재연결 포함)마다 첫 snapshot 을 받으면 스냅샷 API 로 전체를 다시 불러온다.
//    - 첫 연결: 페이지를 연 뒤 ~ 구독이 시작되기 전 사이에 배치·매장 정보가 바뀌었을 수 있음
//      (그 사이 변경은 구독자가 없어서 이벤트로 오지 않는다)
//    - 재연결: 끊긴 동안의 변경도 마찬가지
//    snapshot 은 상태만 담고 있으므로, 배치·매장 정보까지 맞추려면 전체를 다시 불러와야 한다.
// =====================================================

const RETRY_MIN_MS = 3000;    // 서버가 에러로 응답해 브라우저가 재연결을 포기했을 때 직접 다시 연결하는 간격
const RETRY_MAX_MS = 30000;
const RELOAD_RETRY_MIN_MS = 2000;   // 다시 불러오기가 실패했을 때 다시 시도하는 간격
const RELOAD_RETRY_MAX_MS = 30000;

let liveSource = null;
let firstOnConnection = true;    // 이번 연결에서 아직 snapshot 을 못 받았는지
let retryDelay = RETRY_MIN_MS;
let retryTimer = null;

function connectLive() {
  if (liveSource) liveSource.close();
  liveSource = new EventSource(`/api/public/stores/${encodeURIComponent(PUBLIC_ID)}/events`);
  firstOnConnection = true;

  // 연결이 (다시) 열릴 때마다. 브라우저가 알아서 재연결해도 여기로 온다
  liveSource.onopen = () => {
    firstOnConnection = true;
    setLiveState(true);
    retryDelay = RETRY_MIN_MS;
  };

  // snapshot 은 두 경우에 온다
  //   1) 연결 직후 (연결마다 첫 번째) → 전체 다시 불러오기
  //   2) 같은 연결에서 나중에 = 사장님의 전체 비우기 → 상태 전체 교체
  liveSource.addEventListener("snapshot", (e) => {
    const data = JSON.parse(e.data);
    if (firstOnConnection) {
      firstOnConnection = false;
      reloadAll();
      return;
    }
    applySnapshot(data);
  });

  liveSource.addEventListener("status", (e) => {
    const { tableId, occupied, updatedAt } = JSON.parse(e.data);
    viewer.occupied.set(tableId, occupied);
    touchUpdatedAt(updatedAt);
    redraw();
  });

  liveSource.addEventListener("layout", (e) => {
    const { version } = JSON.parse(e.data);
    // 이미 그 버전 이상을 그리고 있으면 건너뜀
    if (viewer.layout && viewer.layout.version >= version) return;
    reloadAll();
  });

  liveSource.addEventListener("store", (e) => {
    const { name, open } = JSON.parse(e.data);
    if (!viewer.snapshot) return;
    viewer.snapshot.store = { ...viewer.snapshot.store, name, open };
    redraw();
  });

  liveSource.onerror = () => {
    setLiveState(false);
    // CONNECTING: 브라우저가 알아서 다시 연결하는 중 (서버가 꺼졌다 켜지는 경우 등) → 기다리면 됨
    // CLOSED: 서버가 에러로 응답해서 브라우저가 포기함 → 직접 간격을 늘려가며 다시 연결
    if (liveSource.readyState === EventSource.CLOSED) scheduleReconnect();
  };
}

function scheduleReconnect() {
  if (retryTimer) return;
  retryTimer = setTimeout(() => {
    retryTimer = null;
    connectLive();
  }, retryDelay);
  retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS);
}

// 상태 전체 교체 (사장님의 전체 비우기)
function applySnapshot({ statuses, lastUpdatedAt }) {
  if (!viewer.snapshot) return;
  viewer.snapshot.statuses = statuses;
  viewer.snapshot.lastUpdatedAt = lastUpdatedAt;
  viewer.occupied = new Map(statuses.map((s) => [s.tableId, s.occupied]));
  redraw();
}

// 마지막 업데이트 시각: 더 최근 값일 때만 바꿈
function touchUpdatedAt(iso) {
  if (!viewer.snapshot || !iso) return;
  const cur = viewer.snapshot.lastUpdatedAt;
  if (!cur || Date.parse(iso) > Date.parse(cur)) viewer.snapshot.lastUpdatedAt = iso;
}

// 지금 화면 다시 그리기. 배치가 없는 매장은 안내 화면만 갱신
//   다시 불러오는 중에 온 이벤트는, 곧 도착할 (이벤트보다 옛날일 수 있는) 응답이 덮어쓸 수 있다
//   → 응답을 그린 뒤 한 번 더 불러오도록 표시해 둔다
function redraw() {
  if (reloading) reloadAgain = true;
  if (viewer.layout) refresh();
  else showNoLayout(viewer.snapshot);
}

// ---------- 스냅샷 API 로 전체 다시 불러오기 ----------
//   - 여러 번 겹쳐 오면 하나로 합친다 (진행 중이면 끝난 뒤 한 번 더)
//   - 실패하면 간격을 늘려가며 성공할 때까지 다시 시도한다
//     (연결은 멀쩡하면 재연결이 또 일어나지 않으므로, 여기서 직접 다시 시도하지 않으면 옛 화면이 계속 남는다)
let reloading = null;
let reloadAgain = false;
let reloadRetryDelay = RELOAD_RETRY_MIN_MS;
let reloadRetryTimer = null;

async function reloadAll() {
  if (reloading) {
    reloadAgain = true;
    return reloading;
  }
  clearTimeout(reloadRetryTimer);
  reloadRetryTimer = null;

  reloading = (async () => {
    do {
      reloadAgain = false;
      try {
        const data = await loadData();
        render(data);
        reloadRetryDelay = RELOAD_RETRY_MIN_MS;
      } catch (e) {
        console.warn(`다시 불러오기 실패 → ${reloadRetryDelay / 1000}초 뒤 다시 시도`, e);
        scheduleReloadRetry();
        return;
      }
    } while (reloadAgain);
  })();

  try {
    await reloading;
  } finally {
    reloading = null;
  }
}

function scheduleReloadRetry() {
  if (reloadRetryTimer) return;
  reloadRetryTimer = setTimeout(() => {
    reloadRetryTimer = null;
    reloadAll();
  }, reloadRetryDelay);
  reloadRetryDelay = Math.min(reloadRetryDelay * 2, RELOAD_RETRY_MAX_MS);
}

// 연결 상태 안내: 끊겨 있는 동안만 보임
function setLiveState(connected) {
  const el = document.getElementById("live");
  if (el) el.hidden = connected;
}
