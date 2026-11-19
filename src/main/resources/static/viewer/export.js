// =====================================================
//  자리있어 출력 모드 (#9)
//  상태 색 없이 그린 배치도를 PNG(base64)로 만들어 앱에 전달한다.
//    ?mode=export              → 손님 모드와 같은 기본 층
//    ?mode=export&floor=f2     → 그 층만
//    ?mode=export&floor=all    → 모든 층을 위에서 아래로 한 장에
// =====================================================

const EXPORT_SCALE = 2;      // 선명하게 2배로 그림
const EXPORT_GAP = 24;       // 층 사이 여백 (px, 배율 적용 전)
const EXPORT_TITLE_H = 32;   // 층 이름 줄 높이 (여러 층일 때만)
const EXPORT_FONT = '-apple-system, "Apple SD Gothic Neo", "Noto Sans KR", "Malgun Gothic", sans-serif';

// SVG 를 이미지로 바꾸면 style.css 가 적용되지 않는다 → 스타일을 SVG 안에 넣어준다
function collectCss() {
  let css = `svg { font-family: ${EXPORT_FONT}; }\n`;
  for (const sheet of document.styleSheets) {
    try {
      for (const rule of sheet.cssRules) css += rule.cssText + "\n";
    } catch {
      // 다른 출처의 스타일시트는 읽을 수 없음 → 건너뜀
    }
  }
  return css;
}

// 그려진 SVG → 이미지
function svgToImage(svg) {
  const [, , w, h] = svg.getAttribute("viewBox").split(" ").map(Number);
  const clone = svg.cloneNode(true);
  clone.setAttribute("xmlns", SVG_NS);
  clone.setAttribute("width", w);
  clone.setAttribute("height", h);
  const style = document.createElementNS(SVG_NS, "style");
  style.textContent = collectCss();
  clone.prepend(style);

  const xml = new XMLSerializer().serializeToString(clone);
  const img = new Image();
  return new Promise((resolve, reject) => {
    img.onload = () => resolve({ img, w, h });
    img.onerror = () => reject(new Error("배치도를 이미지로 바꾸지 못했어요"));
    img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(xml);
  });
}

// 층들을 그려서 위아래로 이어 붙인 PNG(base64, 앞의 data:image/png;base64, 는 뺌)
async function exportFloors(floors) {
  const showTitle = floors.length > 1;
  const parts = [];
  for (const floor of floors) {
    const svg = document.createElementNS(SVG_NS, "svg");
    draw(svg, floor);   // 상태(occupied) 없이 → 상태 색 없음
    parts.push({ floor, ...(await svgToImage(svg)) });
  }

  const width = Math.max(...parts.map((p) => p.w));
  const height = parts.reduce((sum, p) => sum + p.h + (showTitle ? EXPORT_TITLE_H : 0), 0)
    + EXPORT_GAP * (parts.length - 1);

  const canvas = document.createElement("canvas");
  canvas.width = Math.ceil(width * EXPORT_SCALE);
  canvas.height = Math.ceil(height * EXPORT_SCALE);
  const ctx = canvas.getContext("2d");
  ctx.scale(EXPORT_SCALE, EXPORT_SCALE);
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, 0, width, height);

  let y = 0;
  for (const p of parts) {
    if (showTitle) {
      ctx.fillStyle = "#222";
      ctx.font = `800 18px ${EXPORT_FONT}`;
      ctx.textBaseline = "middle";
      ctx.fillText(p.floor.name, 12, y + EXPORT_TITLE_H / 2);
      y += EXPORT_TITLE_H;
    }
    ctx.drawImage(p.img, (width - p.w) / 2, y, p.w, p.h);   // 가운데 정렬
    y += p.h + EXPORT_GAP;
  }

  return canvas.toDataURL("image/png").split(",")[1];
}

// 출력 모드 실행: PNG 를 만들어 앱에 보내고, 브라우저 확인용으로 화면에도 보여준다
async function runExport(floors) {
  const base64 = await exportFloors(floors);
  const preview = document.getElementById("exportPreview");
  preview.src = "data:image/png;base64," + base64;
  preview.hidden = false;
  callBridge("onExport", base64);
}
