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
//  연결이 끊기면 브라우저 EventSource 가 알아서 다시 연결하고, 다시 연결되면 서버가 snapshot 을 또 보낸다.
//  끊긴 동안 배치·매장 정보가 바뀌었을 수도 있으므로, 재연결 뒤에는 스냅샷 API 로 전체를 다시 불러온다.
// =====================================================

const RETRY_MIN_MS = 3000;    // 브라우저가 포기했을 때(서버가 에러 응답) 직접 다시 연결하는 간격
const RETRY_MAX_MS = 30000;

let liveSource = null;
let liveConnectedOnce = false;   // 한 번이라도 snapshot 을 받았는지 (그 뒤의 연결은 재연결)
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
  //   1) 연결 직후 (연결마다 첫 번째)  2) 같은 연결에서 나중에 = 사장님의 전체 비우기
  liveSource.addEventListener("snapshot", (e) => {
    const data = JSON.parse(e.data);
    const reconnected = firstOnConnection && liveConnectedOnce;
    firstOnConnection = false;
    liveConnectedOnce = true;

    if (reconnected) {
      // 재연결: 끊긴 사이 배치·매장 정보가 바뀌었을 수 있음 → 스냅샷 API 로 전체를 다시 불러옴
      reloadAll();
    } else {
      applySnapshot(data);
    }
  });

  liveSource.addEventListener("status", (e) => {
    const { tableId, occupied, updatedAt } = JSON.parse(e.data);
    viewer.occupied.set(tableId, occupied);
    touchUpdatedAt(updatedAt);
    redraw();
  });

  liveSource.addEventListener("layout", (e) => {
    const { version } = JSON.parse(e.data);
    // 이미 그 버전을 그리고 있으면(재연결로 먼저 다시 불러온 경우 등) 건너뜀
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
    connectLive();   // 다시 붙어서 snapshot 이 오면 '재연결'로 처리 → 전체 다시 불러오기
  }, retryDelay);
  retryDelay = Math.min(retryDelay * 2, RETRY_MAX_MS);
}

// 상태 전체 교체 (연결 직후 snapshot, 사장님의 전체 비우기)
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
function redraw() {
  if (viewer.layout) refresh();
  else showNoLayout(viewer.snapshot);
}

// 스냅샷 API 로 전체 다시 불러오기. 여러 번 겹쳐 오면 하나로 합친다
let reloading = null;
let reloadAgain = false;

async function reloadAll() {
  if (reloading) {
    reloadAgain = true;
    return reloading;
  }
  reloading = (async () => {
    do {
      reloadAgain = false;
      try {
        render(await loadData());
      } catch (e) {
        // 지금 화면은 그대로 두고, 다음 재연결 때 다시 시도
        console.warn("다시 불러오기 실패", e);
      }
    } while (reloadAgain);
  })();
  try {
    await reloading;
  } finally {
    reloading = null;
  }
}

// 연결 상태 안내: 끊겨 있는 동안만 보임
function setLiveState(connected) {
  const el = document.getElementById("live");
  if (el) el.hidden = connected;
}
