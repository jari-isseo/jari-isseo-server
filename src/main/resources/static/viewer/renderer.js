// =====================================================
//  자리있어 웹 렌더러 (#4: 투영 함수 + SVG 뼈대)
// =====================================================

// ---------- 설정 ----------
const SCALE = 60;      // 1m = 60px (가로 기준). 2:1 이라 세로는 30px
const PADDING = 40;    // 그림 바깥 여백 (px)
const SVG_NS = "http://www.w3.org/2000/svg";

// ---------- 투영 함수 ----------
// 바닥 좌표 (x, z) [m] → 화면 좌표 { sx, sy } [px]
//   x 가 커지면 오른쪽 아래, z 가 커지면 왼쪽 아래로 간다.
//   (0,0) → (0,0), (1,0) → (60,30), (0,1) → (-60,30)
function toScreen(x, z) {
  return {
    sx: (x - z) * SCALE,
    sy: (x + z) * SCALE / 2,
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

// ---------- 그리기 ----------
// 지금은 테이블 중심점과 좌표 확인용 기준점만 찍는다.
// 바닥·벽(#5), 테이블·의자 도형(#6), 상태 색(#7)은 다음 이슈에서.
function draw(svg, floor) {
  svg.innerHTML = "";
  const points = [];   // viewBox 계산용 (화면 좌표 모음)

  // 1) 기준점: (0,0) (1,0) (0,1)
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

  // 2) 테이블 중심점
  for (const t of floor.tables) {
    const p = toScreen(t.x, t.z);
    points.push(p);
    svgEl("circle", { cx: p.sx, cy: p.sy, r: 4, class: "table-dot" }, svg);
    addText(svg, p.sx + 6, p.sy + 4, t.name, "table-label");
  }

  // 3) 점이 전부 보이도록 viewBox 맞추기
  const xs = points.map((p) => p.sx);
  const ys = points.map((p) => p.sy);
  const minX = Math.min(...xs) - PADDING;
  const minY = Math.min(...ys) - PADDING;
  const w = Math.max(...xs) - Math.min(...xs) + PADDING * 2;
  const h = Math.max(...ys) - Math.min(...ys) + PADDING * 2;
  svg.setAttribute("viewBox", `${minX} ${minY} ${w} ${h}`);
}

// ---------- 시작 ----------
async function main() {
  // 투영 함수 확인용 로그 (개발자도구 Console 에서 확인)
  console.table({
    "(0,0)": toScreen(0, 0),
    "(1,0)": toScreen(1, 0),
    "(0,1)": toScreen(0, 1),
  });

  try {
    const { name, layout } = await loadSample();
    const floors = [...layout.floors].sort((a, b) => a.order - b.order);
    const floor = floors[0];   // 지금은 order 0 층만. 층 선택은 이후 이슈에서

    document.getElementById("title").textContent = floor.name;
    document.getElementById("info").textContent =
      `sample=${name} · 테이블 ${floor.tables.length}개 · 전체 ${floors.length}개 층`;

    draw(document.getElementById("stage"), floor);
  } catch (e) {
    document.getElementById("title").textContent = "불러오기 실패";
    document.getElementById("info").textContent = e.message;
    console.error(e);
  }
}

main();
