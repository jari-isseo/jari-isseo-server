// =====================================================
//  자리있어?! 웹 렌더러
//  #4: 투영 함수 + SVG 뼈대
//  #5: 바닥면 + 벽 (뒤쪽 벽만 높게, 앞쪽은 테두리선)
//  #6: 테이블·의자 도형 + 가림 순서
//  #7: 상태 색·테이블 이름·빈자리 요약·정보 오래됨 표시
//  #8: 테이블 탭 판정 + 화면 크기 자동 맞춤
//  #9: mode 분기(손님·사장님·출력) + 앱 브리지 (bridge.js, export.js)
// =====================================================

// ---------- 설정 ----------
const SCALE = 60;        // 1m = 60px (가로 기준). 2:1 이라 바닥 세로는 30px
const HEIGHT_SCALE = 60; // 높이 1m = 화면 위로 60px
const WALL_HEIGHT = 0.8;     // 낮은 벽 높이 (m) — 안쪽이 보이도록 낮게
const WALL_THICKNESS = 0.12; // 벽 두께 (m) — 윗면이 살짝 보이게
const PADDING = 12;      // 그림 바깥 여백 (px). 작을수록 매장이 크게 보임 (#8)

// 가구 크기 (m) — 실제 높이보다 낮게 잡아 시안처럼 납작한 상자로 보이게
const TABLE_HEIGHT = 0.35;
const CHAIR_HEIGHT = 0.22;
const CHAIR_SIZE = 0.4;
const ROUND_SEGMENTS = 24;   // 원형 테이블을 몇 각형으로 근사할지
const SVG_NS = "http://www.w3.org/2000/svg";
const PARAMS = new URLSearchParams(location.search);
const DEBUG = PARAMS.get("debug") === "1";
// 모드 (API 정의서 6장): 없음 = 손님(조회 전용) / owner = 사장님 앱 WebView / export = PNG 출력
const MODE = ["owner", "export"].includes(PARAMS.get("mode")) ? PARAMS.get("mode") : "customer";
const STALE_MINUTES = 30;   // 마지막 상태 변경 후 이 시간이 지나면 '정보 오래됨' (AC-05)
const MIN_TOUCH_RADIUS_PX = 22; // 작은 테이블도 반지름 22px(지름 44px) 안을 누르면 탭으로 인정 (NFR-04)

// ---------- 투영 함수 ----------
// 바닥 좌표 (x, z) [m] + 높이 h [m] → 화면 좌표 { sx, sy } [px]
//   x 가 커지면 오른쪽 아래, z 가 커지면 왼쪽 아래, h 가 커지면 위로.
//   (0,0) → (0,0), (1,0) → (60,30), (0,1) → (-60,30)
function toScreen(x, z, h = 0) {
  return {
    sx: (x - z) * SCALE,
    sy: (x + z) * SCALE / 2 - h * HEIGHT_SCALE,
  };
}

// ---------- 개발용: 샘플 JSON 불러오기 ----------
// ?sample=layout (기본) | nowall | snapshot
// Live Server 를 레포 루트에서 열었을 때 /docs/samples/ 에서 읽는다.
const SAMPLE_FILES = {
  layout: "sample-layout.json",
  nowall: "sample-layout-nowall.json",
  snapshot: "sample-snapshot.json",
};

async function loadSample() {
  const name = new URLSearchParams(location.search).get("sample") || "layout";
  const file = SAMPLE_FILES[name];
  if (!file) throw new Error(`알 수 없는 sample 값: ${name}`);

  const res = await fetch(`/docs/samples/${file}`);
  if (!res.ok) throw new Error(`${file} 을 못 읽었어요 (HTTP ${res.status})`);
  const data = await res.json();

  // 스냅샷은 { store, layout, statuses, lastUpdatedAt } 이고 배치는 layout 안에 있다.
  // 배치 파일만 열면 상태 정보가 없다 (snapshot: null)
  return name === "snapshot"
    ? { name, layout: data.layout, snapshot: data }
    : { name, layout: data, snapshot: null };
}

// ---------- SVG 도우미 ----------
function svgEl(tag, attrs, parent) {
  const el = document.createElementNS(SVG_NS, tag);
  for (const [k, v] of Object.entries(attrs)) el.setAttribute(k, v);
  if (parent) parent.appendChild(el);
  return el;
}

function addText(parent, x, y, text, cls, extra = {}) {
  const t = svgEl("text", { x, y, class: cls, ...extra }, parent);
  t.textContent = text;
  return t;
}

// 화면 좌표 배열 → SVG points 문자열
function toPoints(screenPts) {
  return screenPts.map((p) => `${p.sx},${p.sy}`).join(" ");
}

// ---------- 기하 도우미 ----------
// 점 (x, z) 가 다각형 안에 있는지 (ray casting)
function isInside(polygon, x, z) {
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, zi] = polygon[i];
    const [xj, zj] = polygon[j];
    const crosses = (zi > z) !== (zj > z) &&
      x < ((xj - xi) * (z - zi)) / (zj - zi) + xi;
    if (crosses) inside = !inside;
  }
  return inside;
}

// 벽 선분의 "바닥 안쪽" 방향 단위벡터
//   벽 가운데에서 양쪽으로 살짝 나가 보고, 바닥 안에 있는 쪽을 안쪽으로 본다.
//   양쪽 다 안쪽(칸막이 벽)이거나 양쪽 다 바깥이면 안쪽을 정할 수 없으므로,
//   from/to 순서와 상관없이 항상 화면 앞쪽(x + z 가 커지는 쪽)을 향하는 방향을 쓴다.
//   → 칸막이 벽은 끝점 순서가 바뀌어도 늘 같은 모양(세운 벽)으로 그려진다.
function inwardNormal(polygon, from, to) {
  const dx = to[0] - from[0];
  const dz = to[1] - from[1];
  const len = Math.hypot(dx, dz) || 1;
  const n = { x: -dz / len, z: dx / len };   // 선분에 수직인 한쪽 방향
  const flipped = { x: -n.x, z: -n.z };
  const mx = (from[0] + to[0]) / 2;
  const mz = (from[1] + to[1]) / 2;
  const EPS = 0.05;

  const sideA = isInside(polygon, mx + n.x * EPS, mz + n.z * EPS);
  const sideB = isInside(polygon, mx - n.x * EPS, mz - n.z * EPS);
  if (sideA !== sideB) return sideA ? n : flipped;   // 바깥 벽: 안쪽이 분명함

  // 칸막이(양쪽 안쪽) 또는 바닥 밖의 벽: 화면 앞쪽을 향하는 쪽으로 고정
  const towardViewer = n.x + n.z;
  if (Math.abs(towardViewer) > 1e-9) return towardViewer > 0 ? n : flipped;
  return n.x > 0 ? n : flipped;   // 시선과 정확히 나란한 벽: 그래도 순서와 무관하게 하나로
}

// 화면 앞쪽은 x + z 가 커지는 방향.
// 안쪽 면이 앞쪽(우리 쪽)을 보면 → 뒤쪽 벽 → 높게 그린다.
function isBackWall(normal) {
  return normal.x + normal.z > 1e-6;
}

// walls 가 비어 있으면 바닥 외곽선의 각 변을 벽으로 쓴다 (API 정의서 2장)
function wallsOf(floor) {
  if (floor.walls && floor.walls.length > 0) return floor.walls;
  const poly = floor.floor.polygon;
  return poly.map((p, i) => ({ id: `edge-${i}`, from: p, to: poly[(i + 1) % poly.length] }));
}

// ---------- 그리기 ----------
function drawFloor(svg, floor, points) {
  const screen = floor.floor.polygon.map(([x, z]) => toScreen(x, z));
  points.push(...screen);
  svgEl("polygon", { points: toPoints(screen), class: "floor" }, svg);
}

// 뒤쪽 벽은 높이 있는 면으로, 앞쪽 벽·변은 바닥 위 테두리선으로
function drawWalls(svg, floor, points) {
  const poly = floor.floor.polygon;
  const back = [];
  const front = [];

  for (const w of wallsOf(floor)) {
    const n = inwardNormal(poly, w.from, w.to);
    (isBackWall(n) ? back : front).push(w);
  }

  // 1) 앞쪽 벽·변: 테두리선만 (테이블을 가리지 않게)
  for (const w of front) {
    const a = toScreen(w.from[0], w.from[1]);
    const b = toScreen(w.to[0], w.to[1]);
    svgEl("line", { x1: a.sx, y1: a.sy, x2: b.sx, y2: b.sy, class: "wall-front" }, svg);
  }

  // 2) 뒤쪽 벽: 바닥선 → 윗선까지 면을 채운다
  //    멀리 있는 벽(x+z 작은 것)부터 그려야 겹칠 때 자연스럽다
  back.sort((a, b) => (a.from[0] + a.from[1] + a.to[0] + a.to[1]) -
                      (b.from[0] + b.from[1] + b.to[0] + b.to[1]));
  for (const w of back) {
    const [x1, z1] = w.from;
    const [x2, z2] = w.to;
    const face = [
      toScreen(x1, z1, 0), toScreen(x2, z2, 0),
      toScreen(x2, z2, WALL_HEIGHT), toScreen(x1, z1, WALL_HEIGHT),
    ];
    points.push(...face);
    const dir = Math.abs(x2 - x1) >= Math.abs(z2 - z1) ? "along-x" : "along-z";
    svgEl("polygon", { points: toPoints(face), class: `wall-face ${dir}` }, svg);

    // 윗면: 벽을 바깥쪽으로 두께만큼 밀어낸 띠 (두께감)
    //   양 끝도 두께만큼 늘려서, 두 벽이 만나는 모서리에 빈틈이 없게 한다
    const n = inwardNormal(poly, w.from, w.to);
    const ox = -n.x * WALL_THICKNESS;
    const oz = -n.z * WALL_THICKNESS;
    const len = Math.hypot(x2 - x1, z2 - z1) || 1;
    const ex = ((x2 - x1) / len) * WALL_THICKNESS;
    const ez = ((z2 - z1) / len) * WALL_THICKNESS;
    const top = [
      toScreen(x1 - ex, z1 - ez, WALL_HEIGHT), toScreen(x2 + ex, z2 + ez, WALL_HEIGHT),
      toScreen(x2 + ex + ox, z2 + ez + oz, WALL_HEIGHT), toScreen(x1 - ex + ox, z1 - ez + oz, WALL_HEIGHT),
    ];
    points.push(...top);
    svgEl("polygon", { points: toPoints(top), class: "wall-top" }, svg);
  }
}

// ---------- 가구 바닥 모양 ----------
// 테이블 바닥 모양: 꼭짓점 [x, z] 배열 (크기·회전·원형 반영)
//   rotation 은 위에서 내려다봤을 때(x 오른쪽, z 아래) 시계 방향 각도(도)
function tableFootprint(t) {
  const w = t.width ?? 0.7;
  const d = t.depth ?? 0.7;

  // 중심 기준 꼭짓점 (회전 전)
  let local;
  if (t.shape === "ROUND") {
    // 원형: width·depth 를 지름으로 하는 원(같지 않으면 타원)을 다각형으로 근사
    local = [];
    for (let i = 0; i < ROUND_SEGMENTS; i++) {
      const a = (i / ROUND_SEGMENTS) * Math.PI * 2;
      local.push([(w / 2) * Math.cos(a), (d / 2) * Math.sin(a)]);
    }
  } else {
    local = [[-w / 2, -d / 2], [w / 2, -d / 2], [w / 2, d / 2], [-w / 2, d / 2]];
  }

  // 회전 후 테이블 중심으로 이동 (타원형 원탁도 회전이 반영됨)
  const r = ((t.rotation ?? 0) * Math.PI) / 180;
  const cos = Math.cos(r);
  const sin = Math.sin(r);
  return local.map(([px, pz]) => [t.x + px * cos - pz * sin, t.z + px * sin + pz * cos]);
}

// 의자 바닥 모양: 중심 기준 작은 정사각형
function chairFootprint(c) {
  const h = CHAIR_SIZE / 2;
  return [[c.x - h, c.z - h], [c.x + h, c.z - h], [c.x + h, c.z + h], [c.x - h, c.z + h]];
}

// ---------- 입체 도형 (바닥 모양을 위로 세운 기둥) ----------
// 꼭짓점 순서를 한 방향(반시계)으로 맞춘다 — 바깥쪽 법선 계산용
function counterClockwise(pts) {
  let area = 0;
  for (let i = 0; i < pts.length; i++) {
    const [x1, z1] = pts[i];
    const [x2, z2] = pts[(i + 1) % pts.length];
    area += x1 * z2 - x2 * z1;
  }
  return area < 0 ? [...pts].reverse() : pts;
}

// 화면에서 보이는 옆면(앞쪽을 향한 면)과 윗면을 그린다.
//   옆면 바깥 법선 (nx, nz) 가 화면 앞쪽(x + z 증가)을 향하면 보이는 면.
//   오른쪽(x)을 향한 면과 왼쪽 아래(z)를 향한 면의 밝기를 다르게 해 입체감을 준다.
function drawPrism(svg, footprint, height, cls, points) {
  const pts = counterClockwise(footprint);
  const g = svgEl("g", { class: cls }, svg);
  const polygons = [];   // 화면에 그린 면들(화면 좌표) — 탭 판정에 사용

  for (let i = 0; i < pts.length; i++) {
    const [x1, z1] = pts[i];
    const [x2, z2] = pts[(i + 1) % pts.length];
    const nx = z2 - z1;            // 반시계 순서일 때 바깥쪽 법선
    const nz = -(x2 - x1);
    if (nx + nz <= 1e-9) continue; // 뒤를 향한 면은 안 보임

    const face = [
      toScreen(x1, z1, 0), toScreen(x2, z2, 0),
      toScreen(x2, z2, height), toScreen(x1, z1, height),
    ];
    points.push(...face);
    polygons.push(face);
    svgEl("polygon", { points: toPoints(face), class: nx > nz ? "side-x" : "side-z" }, g);
  }

  const top = pts.map(([x, z]) => toScreen(x, z, height));
  points.push(...top);
  polygons.push(top);
  svgEl("polygon", { points: toPoints(top), class: "top" }, g);
  return { g, polygons };
}

// ---------- 가림 순서 ----------
// 바닥 모양을 감싸는 사각형 범위
function boundsOf(footprint) {
  const xs = footprint.map((p) => p[0]);
  const zs = footprint.map((p) => p[1]);
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minZ: Math.min(...zs), maxZ: Math.max(...zs) };
}

// 화면에 그려질 범위 (겹치는지 판단용)
function screenBoundsOf(footprint, height) {
  const pts = footprint.flatMap(([x, z]) => [toScreen(x, z, 0), toScreen(x, z, height)]);
  const xs = pts.map((p) => p.sx);
  const ys = pts.map((p) => p.sy);
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) };
}

function screenOverlaps(a, b) {
  return a.minX < b.maxX && b.minX < a.maxX && a.minY < b.maxY && b.minY < a.maxY;
}

// a 가 b 보다 확실히 뒤(화면 위쪽)에 있으면 true.
//   시점은 x·z 가 큰 쪽(앞)에서 내려다보므로,
//   a 가 x 방향으로 b 보다 완전히 작거나, z 방향으로 완전히 작으면 a 가 뒤다.
//   (중심점만 비교하면 긴 테이블 옆의 작은 테이블에서 순서가 뒤집힐 수 있다)
function isBehind(a, b) {
  return a.bounds.maxX <= b.bounds.minX + 1e-9 || a.bounds.maxZ <= b.bounds.minZ + 1e-9;
}

// 화면에서 겹치는 가구끼리만 "뒤 → 앞" 관계를 만들고 그 순서대로 정렬한다 (위상 정렬).
//   관계를 정할 수 없는 경우(바닥 범위가 서로 겹침)와 남은 동률은 중심 깊이(x + z)로 정한다.
function sortByDepth(items) {
  const n = items.length;
  const after = items.map(() => []);   // after[i] = i 보다 나중에 그려야 하는 것들
  const inDegree = new Array(n).fill(0);

  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const a = items[i];
      const b = items[j];
      if (!screenOverlaps(a.screen, b.screen)) continue;   // 안 겹치면 순서 상관없음

      let behind;   // 먼저 그릴 쪽
      if (isBehind(a, b)) behind = i;
      else if (isBehind(b, a)) behind = j;
      else behind = (a.depth - b.depth || a.height - b.height) <= 0 ? i : j;

      const front = behind === i ? j : i;
      after[behind].push(front);
      inDegree[front]++;
    }
  }

  // 먼저 그릴 수 있는 것 중 중심 깊이가 작은 것부터 꺼낸다
  const byDepth = (x, y) => items[x].depth - items[y].depth || items[x].height - items[y].height;
  const ready = [];
  for (let i = 0; i < n; i++) if (inDegree[i] === 0) ready.push(i);
  const order = [];
  const done = new Array(n).fill(false);

  while (order.length < n) {
    if (ready.length === 0) {
      // 순환이 생긴 드문 경우: 남은 것 중 가장 뒤의 것을 강제로 꺼낸다
      const rest = [...Array(n).keys()].filter((i) => !done[i]).sort(byDepth);
      ready.push(rest[0]);
    }
    ready.sort(byDepth);
    const i = ready.shift();
    if (done[i]) continue;
    done[i] = true;
    order.push(items[i]);
    for (const k of after[i]) {
      if (--inDegree[k] === 0 && !done[k]) ready.push(k);
    }
  }
  return order;
}

// 테이블·의자를 뒤(화면 위쪽)부터 그린다 → 앞의 것이 뒤의 것을 덮음
//   occupied: tableId → true(손님 있음) / false(빈자리). 상태를 모르면 Map 에 없음 → 회색
//   반환값: 탭 판정용 테이블 목록 (그린 순서 = 뒤 → 앞)
//   pending: 앱 응답을 기다리는 tableId 모음 (사장님 모드, #9) → 흐리게
function drawFurniture(svg, floor, points, occupied, pending) {
  const hitTargets = [];
  const items = [
    ...(floor.chairs ?? []).map((c) => ({ kind: "chair", item: c, footprint: chairFootprint(c), height: CHAIR_HEIGHT })),
    ...(floor.tables ?? []).map((t) => ({ kind: "table", item: t, footprint: tableFootprint(t), height: TABLE_HEIGHT })),
  ].map((it) => ({
    ...it,
    depth: it.item.x + it.item.z,
    bounds: boundsOf(it.footprint),
    screen: screenBoundsOf(it.footprint, it.height),
  }));

  for (const { kind, item, footprint, height } of sortByDepth(items)) {
    if (kind === "chair") {
      drawPrism(svg, footprint, height, "chair", points);
    } else {
      const state = occupied.has(item.id) ? (occupied.get(item.id) ? "busy" : "free") : "";
      const pend = pending.has(item.id) ? "pending" : "";
      const { g, polygons } = drawPrism(svg, footprint, height, `table ${state} ${pend}`.replace(/\s+/g, " ").trim(), points);
      g.dataset.tableId = item.id;   // 탭 처리·실시간 갱신(4-3)에서 테이블을 찾을 때 사용

      // 키보드·스크린리더로도 고를 수 있게 (Tab 으로 이동, Enter/Space 로 선택)
      const stateText = pend ? "변경 중" : state === "free" ? "빈자리" : state === "busy" ? "사용 중" : "상태 모름";
      g.setAttribute("tabindex", "0");
      g.setAttribute("role", "button");
      g.setAttribute("aria-label", `${item.name} 테이블, ${stateText}`);

      // 테이블 이름: 윗면 가운데
      const p = toScreen(item.x, item.z, height);
      addText(svg, p.sx, p.sy, item.name, `table-label ${state}`.trim());

      hitTargets.push({ id: item.id, name: item.name, group: g, polygons, center: p });
    }
  }
  return hitTargets;
}

// ---------- 테이블 탭 판정 (#8) ----------
// 화면 좌표 점이 다각형 안에 있는지
function pointInScreenPolygon(poly, x, y) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i];
    const b = poly[j];
    if ((a.sy > y) !== (b.sy > y) && x < ((b.sx - a.sx) * (y - a.sy)) / (b.sy - a.sy) + a.sx) {
      inside = !inside;
    }
  }
  return inside;
}

// 손가락 위치(화면 픽셀) → SVG 좌표
function toSvgPoint(svg, clientX, clientY) {
  const pt = svg.createSVGPoint();
  pt.x = clientX;
  pt.y = clientY;
  return pt.matrixTransform(svg.getScreenCTM().inverse());
}

// SVG 좌표 1단위가 실제 화면에서 몇 px 인지 (지도 확대 배율)
function pxPerUnit(svg) {
  const m = svg.getScreenCTM();
  return m ? Math.hypot(m.a, m.b) : 1;
}

// 점에서 선분(a-b)까지 가장 가까운 거리
function distToSegment(x, y, a, b) {
  const dx = b.sx - a.sx;
  const dy = b.sy - a.sy;
  const len2 = dx * dx + dy * dy;
  const t = len2 ? Math.max(0, Math.min(1, ((x - a.sx) * dx + (y - a.sy) * dy) / len2)) : 0;
  return Math.hypot(x - (a.sx + t * dx), y - (a.sy + t * dy));
}

// 점에서 테이블 도형(여러 면)의 테두리까지 가장 가까운 거리
function distToPolygons(polygons, x, y) {
  let min = Infinity;
  for (const poly of polygons) {
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      min = Math.min(min, distToSegment(x, y, poly[j], poly[i]));
    }
  }
  return min;
}

// 눌린 테이블 찾기
//   1) 테이블 도형(윗면·옆면) 안을 눌렀으면 그 테이블. 겹치면 앞(나중에 그린 것)이 우선.
//   2) 도형 밖이어도, 테이블 테두리에서 MIN_TOUCH_RADIUS_PX 안이면 가장 가까운 테이블.
//      → 화면에서 작게 보이는 테이블도 손가락으로 누르기 쉬움 (NFR-04)
//      (가운데 기준이 아니라 테두리 기준이라 큰 테이블도 가장자리 바깥 22px 까지 똑같이 잡힘)
function findTappedTable(svg, hitTargets, clientX, clientY) {
  const p = toSvgPoint(svg, clientX, clientY);

  for (let i = hitTargets.length - 1; i >= 0; i--) {
    if (hitTargets[i].polygons.some((poly) => pointInScreenPolygon(poly, p.x, p.y))) {
      return hitTargets[i];
    }
  }

  const radius = MIN_TOUCH_RADIUS_PX / pxPerUnit(svg);
  let best = null;
  let bestDist = Infinity;
  for (const t of hitTargets) {
    const d = distToPolygons(t.polygons, p.x, p.y);
    if (d <= radius && d < bestDist) {
      best = t;
      bestDist = d;
    }
  }
  return best;
}

// 테이블 선택 → tabletap 이벤트. 탭·키보드 모두 여기로 모인다.
function selectTable(hit, hitTargets) {
  window.dispatchEvent(new CustomEvent("tabletap", {
    detail: { tableId: hit.id, name: hit.name },
  }));

  if (DEBUG) {
    console.log("tabletap", hit.id, hit.name);
    hitTargets.forEach((t) => t.group.classList.toggle("tapped", t === hit));
  }
}

// 탭·키보드 이벤트 연결. 탭 이후 동작(상태 전환·앱에 알리기)은 2-6에서 이 이벤트를 받아 처리한다.
//   window.addEventListener("tabletap", (e) => e.detail.tableId)
function enableTableTap(svg, hitTargets) {
  svg.onclick = (e) => {
    const hit = findTappedTable(svg, hitTargets, e.clientX, e.clientY);
    if (hit) selectTable(hit, hitTargets);
  };

  // Tab 으로 테이블에 포커스 → Enter 또는 Space 로 선택
  svg.onkeydown = (e) => {
    if (e.key !== "Enter" && e.key !== " ") return;
    const group = e.target.closest?.(".table");
    const hit = hitTargets.find((t) => t.group === group);
    if (!hit) return;
    e.preventDefault();   // Space 로 페이지가 스크롤되는 것 방지
    selectTable(hit, hitTargets);
  };
}

// 개발용 기준점: (0,0) (1,0) (0,1) — ?debug=1 일 때만
function drawAxes(svg, points) {
  const axes = [
    { x: 0, z: 0, label: "(0,0)", color: "#d33" },
    { x: 1, z: 0, label: "x (1,0)", color: "#1f6feb" },
    { x: 0, z: 1, label: "z (0,1)", color: "#8250df" },
  ];
  for (const a of axes) {
    const p = toScreen(a.x, a.z);
    points.push(p);
    svgEl("circle", { cx: p.sx, cy: p.sy, r: 5, class: "axis-dot", fill: a.color }, svg);
    addText(svg, p.sx + 8, p.sy - 6, a.label, "axis-label", { fill: a.color });
  }
}

// 그린 것이 전부 보이도록 viewBox 맞추기
function fitViewBox(svg, points) {
  const xs = points.map((p) => p.sx);
  const ys = points.map((p) => p.sy);
  const minX = Math.min(...xs) - PADDING;
  const minY = Math.min(...ys) - PADDING;
  const w = Math.max(...xs) - Math.min(...xs) + PADDING * 2;
  const h = Math.max(...ys) - Math.min(...ys) + PADDING * 2;
  svg.setAttribute("viewBox", `${minX} ${minY} ${w} ${h}`);
}

// 그리는 순서 = 겹치는 순서: 바닥 → 벽 → 가구(테이블·의자)
function draw(svg, floor, occupied = new Map(), pending = new Set()) {
  svg.innerHTML = "";
  const points = [];   // viewBox 계산용 (화면 좌표 모음)

  drawFloor(svg, floor, points);
  drawWalls(svg, floor, points);
  const hitTargets = drawFurniture(svg, floor, points, occupied, pending);
  if (DEBUG) drawAxes(svg, points);

  // viewBox + preserveAspectRatio(meet) → 화면 크기·방향과 상관없이 매장 전체가 보이고 가운데 정렬됨.
  // 화면을 돌리거나 창 크기가 바뀌어도 브라우저가 자동으로 다시 맞춘다.
  fitViewBox(svg, points);
  svg.setAttribute("preserveAspectRatio", "xMidYMid meet");

  enableTableTap(svg, hitTargets);
}

// ---------- 상단 요약 (#7) ----------
// 지금 시각. 개발용으로 ?now=2026-11-12T11:32:00Z 처럼 고정할 수 있다 (정보 오래됨 확인용)
function nowTime() {
  const fixed = PARAMS.get("now");
  const t = fixed ? Date.parse(fixed) : Date.now();
  return Number.isNaN(t) ? Date.now() : t;
}

// "방금 / N분 전 / N시간 전 / N일 전"
function timeAgo(iso) {
  const minutes = Math.floor((nowTime() - Date.parse(iso)) / 60000);
  if (minutes < 1) return "방금";
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}시간 전`;
  return `${Math.floor(minutes / (60 * 24))}일 전`;
}

function isStale(iso) {
  return (nowTime() - Date.parse(iso)) / 60000 > STALE_MINUTES;
}

// 매장 전체(모든 층) 테이블 수 세기 (FR-U05)
//   free: 상태가 '빈자리'로 확인된 수, unknown: 상태 정보가 없는 수
function countTables(layout, occupied) {
  const ids = layout.floors.flatMap((f) => (f.tables ?? []).map((t) => t.id));
  const free = ids.filter((id) => occupied.get(id) === false).length;
  const unknown = ids.filter((id) => !occupied.has(id)).length;
  return { free, unknown, total: ids.length };
}

// 시간이 흐르면 바뀌는 부분: "N분 전 업데이트", '정보 오래됨'
//   페이지를 열어둔 채 시간이 지나도 맞도록 주기적으로 다시 계산한다.
let freshnessTimer = null;

function startFreshnessUpdates(baseMeta, lastUpdatedAt, open) {
  const update = () => {
    const $ = (id) => document.getElementById(id);
    $("meta").textContent = baseMeta + (lastUpdatedAt ? ` · ${timeAgo(lastUpdatedAt)} 업데이트` : "");
    // 영업 종료면 오래됨 경고는 의미가 없으므로 숨긴다.
    // 상태가 한 번도 바뀌지 않았으면 lastUpdatedAt 이 없을 수 있음 → 오래됨으로 보지 않는다.
    $("stale").hidden = !(open && lastUpdatedAt && isStale(lastUpdatedAt));
  };

  update();
  if (freshnessTimer) clearInterval(freshnessTimer);
  // ?now= 로 시각을 고정한 경우에는 바뀔 일이 없으므로 타이머를 돌리지 않는다
  if (!PARAMS.get("now")) freshnessTimer = setInterval(update, 30 * 1000);
}

function renderSummary(layout, snapshot, occupied) {
  const $ = (id) => document.getElementById(id);

  if (!snapshot) {
    // 배치 파일만 열었을 때: 상태를 모르므로 요약 대신 안내
    $("store").textContent = "";
    $("headline").textContent = "배치 미리보기";
    $("meta").textContent = "상태 정보가 없어서 테이블이 회색으로 보여요 (?sample=snapshot 으로 확인)";
    $("stale").hidden = true;
    return;
  }

  const { store, lastUpdatedAt } = snapshot;
  const { free, unknown, total } = countTables(layout, occupied);

  // 매장 이름 · 영업 여부
  $("store").innerHTML = "";
  $("store").append(store.name, " · ");
  const openLabel = document.createElement("span");
  openLabel.textContent = store.open ? "영업 중" : "영업 종료";
  if (!store.open) openLabel.className = "closed";
  $("store").append(openLabel);

  // 큰 문구: 영업 종료 > 상태 모름 > 빈자리 있음 > 빈자리 없음 순으로 판단
  $("headline").innerHTML = "";
  if (!store.open) {
    $("headline").textContent = "지금은 영업하지 않아요";
  } else if (total > 0 && unknown === total) {
    $("headline").textContent = "아직 빈자리 정보가 없어요";
  } else if (free > 0) {
    const count = document.createElement("span");
    count.className = "count";
    count.textContent = `${free}개`;
    $("headline").append("지금 빈자리 ", count, " 있어요");
  } else if (unknown > 0) {
    // 확인된 빈자리는 없지만 상태를 모르는 테이블이 있음 → '없다'고 단정하지 않는다
    $("headline").textContent = "확인된 빈자리가 없어요";
  } else {
    $("headline").textContent = "지금은 빈자리가 없어요";
  }

  const baseMeta = `빈 테이블 ${free} / 전체 ${total}` + (unknown > 0 ? ` · 상태 미확인 ${unknown}` : "");
  startFreshnessUpdates(baseMeta, lastUpdatedAt, store.open);
}

// ---------- 화면 상태 (#9) ----------
// applyStatus 등으로 상태가 바뀌면 이 값을 고치고 refresh() 로 다시 그린다
const viewer = { layout: null, snapshot: null, floor: null, occupied: new Map() };

function refresh() {
  renderSummary(viewer.layout, viewer.snapshot, viewer.occupied);
  draw(document.getElementById("stage"), viewer.floor, viewer.occupied, pendingTables);
}

// 그릴 층 고르기: ?floor={floorId} 가 있으면 그 층, 없거나 못 찾으면 order 0 층.
//   출력 모드의 ?floor=all 이면 모든 층.
//   (층 드롭다운·사장님 모드의 마지막 본 층은 #32에서)
function pickFloors(floors) {
  const wanted = PARAMS.get("floor");
  if (wanted === "all" && MODE === "export") return floors;
  const found = floors.find((f) => f.id === wanted);
  if (wanted && !found) console.warn(`floor=${wanted} 층이 없어서 첫 층을 보여줘요`);
  return [found ?? floors[0]];
}

// ---------- 시작 ----------
async function main() {
  document.body.classList.add(`mode-${MODE}`);
  if (DEBUG) {
    console.table({
      "(0,0)": toScreen(0, 0),
      "(1,0)": toScreen(1, 0),
      "(0,1)": toScreen(0, 1),
    });
  }

  try {
    const { name, layout, snapshot } = await loadSample();
    const floors = [...layout.floors].sort((a, b) => a.order - b.order);
    const picked = pickFloors(floors);

    viewer.layout = layout;
    viewer.snapshot = snapshot;
    viewer.floor = picked[0];
    // tableId → 손님 있음 여부
    viewer.occupied = new Map((snapshot?.statuses ?? []).map((s) => [s.tableId, s.occupied]));

    document.getElementById("floorName").textContent = viewer.floor.name;
    document.getElementById("info").textContent =
      `mode=${MODE} · sample=${name} · 테이블 ${viewer.floor.tables?.length ?? 0}개 · 의자 ${viewer.floor.chairs?.length ?? 0}개 · 전체 ${floors.length}개 층` +
      (viewer.floor.walls?.length ? "" : " · walls 없음 → 바닥 경계로 대체");

    if (MODE === "export") {
      // 출력: 상태 색 없이 그려서 PNG 로 (요약·상태는 쓰지 않음)
      draw(document.getElementById("stage"), viewer.floor);
      callBridge("onReady");
      await runExport(picked);
      return;
    }

    if (MODE === "owner") setupOwnerMode();
    refresh();
    callBridge("onReady");
  } catch (e) {
    document.getElementById("headline").textContent = "불러오기 실패";
    document.getElementById("meta").textContent = e.message;
    console.error(e);
  }
}

main();
