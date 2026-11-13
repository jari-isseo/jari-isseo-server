// =====================================================
//  자리있어 웹 렌더러
//  #4: 투영 함수 + SVG 뼈대
//  #5: 바닥면 + 벽 (뒤쪽 벽만 높게, 앞쪽은 테두리선)
// =====================================================

// ---------- 설정 ----------
const SCALE = 60;        // 1m = 60px (가로 기준). 2:1 이라 바닥 세로는 30px
const HEIGHT_SCALE = 60; // 높이 1m = 화면 위로 60px
const WALL_HEIGHT = 0.8;     // 낮은 벽 높이 (m) — 안쪽이 보이도록 낮게
const WALL_THICKNESS = 0.12; // 벽 두께 (m) — 윗면이 살짝 보이게
const PADDING = 40;      // 그림 바깥 여백 (px)
const SVG_NS = "http://www.w3.org/2000/svg";
const DEBUG = new URLSearchParams(location.search).get("debug") === "1";

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

  // 스냅샷은 { store, layout, statuses, ... } 이고 배치는 layout 안에 있다
  return { name, layout: name === "snapshot" ? data.layout : data };
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

// 테이블 중심점 (#6에서 도형으로 교체)
function drawTableDots(svg, floor, points) {
  for (const t of floor.tables) {
    const p = toScreen(t.x, t.z);
    points.push(p);
    svgEl("circle", { cx: p.sx, cy: p.sy, r: 4, class: "table-dot" }, svg);
    addText(svg, p.sx + 6, p.sy + 4, t.name, "table-label");
  }
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

// 그리는 순서 = 겹치는 순서: 바닥 → 벽 → 테이블
function draw(svg, floor) {
  svg.innerHTML = "";
  const points = [];   // viewBox 계산용 (화면 좌표 모음)

  drawFloor(svg, floor, points);
  drawWalls(svg, floor, points);
  drawTableDots(svg, floor, points);
  if (DEBUG) drawAxes(svg, points);

  fitViewBox(svg, points);
}

// ---------- 시작 ----------
async function main() {
  if (DEBUG) {
    console.table({
      "(0,0)": toScreen(0, 0),
      "(1,0)": toScreen(1, 0),
      "(0,1)": toScreen(0, 1),
    });
  }

  try {
    const { name, layout } = await loadSample();
    const floors = [...layout.floors].sort((a, b) => a.order - b.order);
    const floor = floors[0];   // 지금은 order 0 층만. 층 선택은 이후 이슈에서

    document.getElementById("title").textContent = floor.name;
    document.getElementById("info").textContent =
      `sample=${name} · 테이블 ${floor.tables.length}개 · 전체 ${floors.length}개 층` +
      (floor.walls?.length ? "" : " · walls 없음 → 바닥 경계로 대체");

    draw(document.getElementById("stage"), floor);
  } catch (e) {
    document.getElementById("title").textContent = "불러오기 실패";
    document.getElementById("info").textContent = e.message;
    console.error(e);
  }
}

main();
