// =====================================================
//  자리있어 웹 ↔ 사장님 앱(WebView) 브리지 (#9)
//  API 정의서 6장
//
//  웹 → 앱 (Android JavascriptInterface, 이름 OwnerBridge)
//    OwnerBridge.onReady()                    처음 그리기 완료
//    OwnerBridge.onTableTap(tableId, occupied) 사장님이 테이블을 탭 (현재 상태)
//    OwnerBridge.onExport(pngBase64)           출력 모드 PNG 완성
//    OwnerBridge.onSummary(free, total)        사장님 모드: 지금 보이는 층의 빈 테이블 수 / 전체 테이블 수 (#49)
//                                              처음 그릴 때(onReady 전), 상태가 바뀔 때, 층을 바꿀 때
//
//  앱 → 웹 (evaluateJavascript)
//    window.applyStatus(tableId, occupied)     API 성공 → 즉시 반영
//    window.revertStatus(tableId)              API 실패 → 원래대로
//
//  탭 흐름: 웹 onTableTap(t1, false) → 앱 PUT .../t1/status → 성공 applyStatus / 실패 revertStatus
// =====================================================

// 앱(WebView) 안이면 window.OwnerBridge 가 있다.
// 브라우저에서 열면 없으므로, 앱에 보냈을 내용을 콘솔에 대신 찍는다.
function callBridge(name, ...args) {
  const bridge = window.OwnerBridge;
  if (bridge && typeof bridge[name] === "function") {
    bridge[name](...args);
    return;
  }
  // PNG base64 처럼 긴 문자열은 앞부분과 길이만
  const shown = args.map((a) =>
    typeof a === "string" && a.length > 80 ? `${a.slice(0, 40)}… (${a.length}자)` : a);
  console.log(`[OwnerBridge] ${name}`, ...shown);
}

// 앱의 응답(applyStatus / revertStatus)을 기다리는 테이블
//   → 흐리게 표시하고, 응답 전 같은 테이블을 또 눌러도 요청을 다시 보내지 않는다.
const pendingTables = new Set();

// 사장님 모드: 테이블 탭 → 앱에 알림 (상태를 바꾸는 API 는 앱이 호출. 웹은 토큰을 모른다)
function setupOwnerMode() {
  window.addEventListener("tabletap", (e) => {
    const { tableId } = e.detail;
    if (pendingTables.has(tableId)) return;

    // 지금 상태를 보낸다. 상태를 모르는 테이블(회색)은 '빈자리(false)'로 보낸다.
    const occupied = viewer.occupied.get(tableId) === true;
    pendingTables.add(tableId);
    refresh();
    callBridge("onTableTap", tableId, occupied);
  });
}

// 사장님 모드 빈자리 요약 → 앱 (#49)
//   웹 화면의 요약 영역은 숨기고(style.css .mode-owner), 앱이 자기 카드에 이 숫자를 보여준다.
//   - 지금 보이는 층 기준. 상태를 모르는 테이블(회색)은 빈자리에 넣지 않는다
//   - 값이 그대로면 다시 보내지 않는다 (테이블을 눌러 흐려지기만 한 경우 등)
let lastSummary = null;

function reportSummary() {
  const ids = (viewer.floor?.tables ?? []).map((t) => t.id);
  const free = ids.filter((id) => viewer.occupied.get(id) === false).length;
  const key = `${viewer.floor?.id ?? ""}:${free}/${ids.length}`;
  if (key === lastSummary) return;
  lastSummary = key;
  callBridge("onSummary", free, ids.length);
}

// 앱 → 웹. 앱이 문자열로 넘겨도 동작하도록 값을 정리한다.
window.applyStatus = (tableId, occupied) => {
  const id = String(tableId);
  pendingTables.delete(id);
  viewer.occupied.set(id, occupied === true || occupied === "true");
  // 방금 바뀌었으므로 '마지막 업데이트'를 지금으로 → 'N시간 전'·'정보 오래됨'이 바로 사라짐
  // (서버 시각은 이후 SSE status 로 들어오면 그 값으로 덮어씀)
  if (viewer.snapshot) viewer.snapshot.lastUpdatedAt = new Date(nowTime()).toISOString();
  refresh();
};

window.revertStatus = (tableId) => {
  pendingTables.delete(String(tableId));
  refresh();   // 상태는 바꾼 적이 없으므로 흐림만 풀면 원래대로
};
