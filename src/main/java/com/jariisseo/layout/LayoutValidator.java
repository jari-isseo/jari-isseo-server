package com.jariisseo.layout;

import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.layout.dto.LayoutData;
import com.jariisseo.layout.dto.LayoutData.ChairData;
import com.jariisseo.layout.dto.LayoutData.FloorData;
import com.jariisseo.layout.dto.LayoutData.FloorShape;
import com.jariisseo.layout.dto.LayoutData.TableData;
import com.jariisseo.layout.dto.LayoutData.WallData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 배치 검증 + 기본값 채우기 (API 정의서 2장 "서버 검증 규칙")
//   - 층 1~10개, 층 id·order 중복 금지, 층 이름 1~20자
//   - 바닥 다각형 3점 이상, 자기 교차 금지
//   - 층마다 테이블 0~100개 (벽만 먼저 저장하고 사물은 나중에 → 0개 허용, #14 앱과 합의)
//     테이블 id 는 모든 층을 통틀어 중복 금지, 이름 1~20자
//   - 좌표 0~100m, 크기 0.3~3m, 회전 0~359도, 모양 RECT·ROUND
// 하나라도 어기면 400 INVALID_LAYOUT (어느 층·어느 값이 문제인지 message 에 적는다)
final class LayoutValidator {

    static final double MAX_COORD = 100.0;
    static final double MIN_SIZE = 0.3;
    static final double MAX_SIZE = 3.0;
    static final double DEFAULT_SIZE = 0.7;
    static final int MAX_FLOORS = 10;
    static final int MAX_TABLES_PER_FLOOR = 100;   // 최소는 0 (벽만 저장하는 1단계)
    static final int MAX_NAME = 20;
    static final int MAX_ID = 50;   // table_status.table_id 컬럼 길이
    private static final double EPS = 1e-9;

    private LayoutValidator() {
    }

    // 검증을 통과하면 기본값을 채운 배치를 돌려준다 (저장·응답은 이 값으로)
    static LayoutData normalize(LayoutData layout) {
        if (layout == null) {
            throw invalid("배치 정보가 비어 있어요");
        }
        String unit = layout.unit() == null ? "m" : layout.unit();
        if (!unit.equals("m")) {
            throw invalid("단위(unit)는 m 만 쓸 수 있어요");
        }

        List<FloorData> floors = layout.floors();
        if (floors == null || floors.isEmpty() || floors.size() > MAX_FLOORS) {
            throw invalid("층은 1~" + MAX_FLOORS + "개여야 해요");
        }

        Set<String> floorIds = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        Set<String> tableIds = new HashSet<>();   // 모든 층 통틀어
        List<FloorData> normalized = new ArrayList<>();

        for (FloorData floor : floors) {
            if (floor == null) {
                throw invalid("비어 있는 층이 있어요");
            }
            checkId(floor.id(), "층 id");
            if (!floorIds.add(floor.id())) {
                throw invalid("층 id 가 겹쳐요: " + floor.id());
            }
            checkName(floor.name(), "층 이름");
            String where = floor.name() + ": ";

            if (floor.order() == null || floor.order() < 0) {
                throw invalid(where + "order 는 0 이상이어야 해요");
            }
            if (!orders.add(floor.order())) {
                throw invalid(where + "order 가 다른 층과 겹쳐요");
            }

            FloorShape shape = checkFloorShape(floor.floor(), where);
            List<WallData> walls = checkWalls(floor.walls(), where);
            List<TableData> tables = checkTables(floor.tables(), tableIds, where);
            List<ChairData> chairs = checkChairs(floor.chairs(), where);

            normalized.add(new FloorData(floor.id(), floor.name(), floor.order(), shape, walls, tables, chairs));
        }
        return new LayoutData(unit, normalized);
    }

    private static FloorShape checkFloorShape(FloorShape shape, String where) {
        if (shape == null || shape.polygon() == null || shape.polygon().size() < 3) {
            throw invalid(where + "바닥은 꼭짓점이 3개 이상이어야 해요");
        }
        for (double[] point : shape.polygon()) {
            checkPoint(point, where + "바닥 꼭짓점");
        }
        if (!isSimplePolygon(shape.polygon())) {
            throw invalid(where + "바닥 선이 서로 겹치거나 교차해요");
        }
        return shape;
    }

    private static List<WallData> checkWalls(List<WallData> walls, String where) {
        if (walls == null) {
            return List.of();   // 비어 있으면 렌더러가 바닥 경계를 벽으로 그림
        }
        for (WallData wall : walls) {
            if (wall == null) {
                throw invalid(where + "비어 있는 벽이 있어요");
            }
            checkPoint(wall.from(), where + "벽 시작점");
            checkPoint(wall.to(), where + "벽 끝점");
        }
        return walls;
    }

    private static List<TableData> checkTables(List<TableData> tables, Set<String> tableIds, String where) {
        if (tables == null) {
            return List.of();   // 벽만 스캔한 단계
        }
        if (tables.size() > MAX_TABLES_PER_FLOOR) {
            throw invalid(where + "테이블은 층마다 " + MAX_TABLES_PER_FLOOR + "개까지예요");
        }
        List<TableData> normalized = new ArrayList<>();
        for (TableData t : tables) {
            if (t == null) {
                throw invalid(where + "비어 있는 테이블이 있어요");
            }
            checkId(t.id(), where + "테이블 id");
            if (!tableIds.add(t.id())) {
                throw invalid(where + "테이블 id 가 겹쳐요 (모든 층 통틀어 달라야 해요): " + t.id());
            }
            checkName(t.name(), where + "테이블 이름");
            String table = where + t.name() + " ";

            checkCoord(t.x(), table + "x");
            checkCoord(t.z(), table + "z");
            double width = t.width() == null ? DEFAULT_SIZE : t.width();
            double depth = t.depth() == null ? DEFAULT_SIZE : t.depth();
            checkSize(width, table + "가로(width)");
            checkSize(depth, table + "세로(depth)");

            double rotation = t.rotation() == null ? 0 : t.rotation();
            if (!Double.isFinite(rotation) || rotation < 0 || rotation >= 360) {
                throw invalid(table + "회전(rotation)은 0~359도여야 해요");
            }
            String shape = t.shape() == null ? "RECT" : t.shape();
            if (!shape.equals("RECT") && !shape.equals("ROUND")) {
                throw invalid(table + "모양(shape)은 RECT 또는 ROUND 여야 해요");
            }
            normalized.add(new TableData(t.id(), t.name(), t.x(), t.z(), width, depth, rotation, shape));
        }
        return normalized;
    }

    private static List<ChairData> checkChairs(List<ChairData> chairs, String where) {
        if (chairs == null) {
            return List.of();
        }
        for (ChairData c : chairs) {
            if (c == null) {
                throw invalid(where + "비어 있는 의자가 있어요");
            }
            checkCoord(c.x(), where + "의자 x");
            checkCoord(c.z(), where + "의자 z");
        }
        return chairs;
    }

    // ---------- 작은 검사들 ----------

    private static void checkId(String id, String what) {
        if (id == null || id.isBlank() || id.length() > MAX_ID) {
            throw invalid(what + "는 1~" + MAX_ID + "자여야 해요");
        }
    }

    private static void checkName(String name, String what) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME) {
            throw invalid(what + "은 1~" + MAX_NAME + "자여야 해요");
        }
    }

    private static void checkPoint(double[] point, String what) {
        if (point == null || point.length != 2) {
            throw invalid(what + ": [x, z] 형식이어야 해요");
        }
        checkCoord(point[0], what + " x");
        checkCoord(point[1], what + " z");
    }

    private static void checkCoord(Double value, String what) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > MAX_COORD) {
            throw invalid(what + " 좌표는 0~" + (int) MAX_COORD + "m 여야 해요");
        }
    }

    private static void checkSize(double value, String what) {
        if (!Double.isFinite(value) || value < MIN_SIZE || value > MAX_SIZE) {
            throw invalid(what + "는 " + MIN_SIZE + "~" + MAX_SIZE + "m 여야 해요");
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.INVALID_LAYOUT, message);
    }

    // ---------- 다각형 자기 교차 검사 ----------

    // 단순 다각형인가? (변끼리 이웃한 꼭짓점 말고는 만나지 않음, 넓이가 0 이 아님)
    //   ㄱ자·ㄷ자 같은 오목한 모양은 OK, 8자(나비넥타이) 모양은 NO
    static boolean isSimplePolygon(List<double[]> pts) {
        int n = pts.size();
        if (n < 3 || Math.abs(signedArea(pts)) < EPS) {
            return false;   // 점이 모자라거나 한 줄 위에 있음
        }
        for (int i = 0; i < n; i++) {
            if (samePoint(pts.get(i), pts.get((i + 1) % n))) {
                return false;   // 길이 0 인 변 (같은 점 연속)
            }
        }
        for (int i = 0; i < n; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                double[] c = pts.get(j);
                double[] d = pts.get((j + 1) % n);
                boolean adjacent = (j == i + 1) || (i == 0 && j == n - 1);
                if (adjacent) {
                    // 이웃한 두 변은 꼭짓점 하나를 공유하는 게 정상. 그 꼭짓점에서 되돌아가며 겹치면 안 됨
                    if (backtracks(a, b, c, d, i == 0 && j == n - 1)) {
                        return false;
                    }
                    continue;
                }
                if (segmentsIntersect(a, b, c, d)) {
                    return false;
                }
            }
        }
        return true;
    }

    // 이웃한 변이 같은 직선 위에서 반대로 꺾여 서로 겹치는지
    private static boolean backtracks(double[] a, double[] b, double[] c, double[] d, boolean wrap) {
        // wrap 이 아니면 b == c (a→b→d), wrap 이면 d == a (c→a→b)
        double[] p = wrap ? c : a;
        double[] shared = wrap ? a : b;
        double[] q = wrap ? b : d;
        if (Math.abs(cross(p, shared, q)) > EPS) {
            return false;
        }
        double dot = (shared[0] - p[0]) * (q[0] - shared[0]) + (shared[1] - p[1]) * (q[1] - shared[1]);
        return dot < 0;
    }

    private static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(c, d, a);
        double d2 = cross(c, d, b);
        double d3 = cross(a, b, c);
        double d4 = cross(a, b, d);
        if (((d1 > EPS && d2 < -EPS) || (d1 < -EPS && d2 > EPS))
                && ((d3 > EPS && d4 < -EPS) || (d3 < -EPS && d4 > EPS))) {
            return true;   // 서로 엇갈려 교차
        }
        // 한 점이 다른 선분 위에 놓임 (닿기만 해도 단순 다각형이 아님)
        return (Math.abs(d1) <= EPS && onSegment(c, d, a))
                || (Math.abs(d2) <= EPS && onSegment(c, d, b))
                || (Math.abs(d3) <= EPS && onSegment(a, b, c))
                || (Math.abs(d4) <= EPS && onSegment(a, b, d));
    }

    // (b - a) × (c - a): 양수면 c 가 a→b 의 왼쪽, 0 이면 한 직선
    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static boolean onSegment(double[] a, double[] b, double[] p) {
        return p[0] >= Math.min(a[0], b[0]) - EPS && p[0] <= Math.max(a[0], b[0]) + EPS
                && p[1] >= Math.min(a[1], b[1]) - EPS && p[1] <= Math.max(a[1], b[1]) + EPS;
    }

    private static boolean samePoint(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) <= EPS && Math.abs(a[1] - b[1]) <= EPS;
    }

    private static double signedArea(List<double[]> pts) {
        double sum = 0;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i);
            double[] q = pts.get((i + 1) % pts.size());
            sum += p[0] * q[1] - q[0] * p[1];
        }
        return sum / 2;
    }
}
