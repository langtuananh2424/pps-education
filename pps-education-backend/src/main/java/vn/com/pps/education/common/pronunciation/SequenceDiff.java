package vn.com.pps.education.common.pronunciation;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * So khớp hai dãy chuỗi theo đúng thuật toán {@code difflib.SequenceMatcher(autojunk=False)} của Python (không có từ "rác"),
 * để kết quả ghép bài viết với lời nói khớp lab ở cả các ca có nhiều cách chia như nhau: khối khớp dài nhất, hoà thì khối
 * đầu tiên theo dãy a rồi dãy b.
 */
final class SequenceDiff {

    /** tag: equal, replace, delete, insert; đoạn a[i1..i2) tương ứng b[j1..j2). */
    record Op(String tag, int i1, int i2, int j1, int j2) {
    }

    private record Block(int a, int b, int size) {
    }

    private final List<String> a;
    private final List<String> b;
    private final Map<String, List<Integer>> b2j = new HashMap<>();

    private SequenceDiff(List<String> a, List<String> b) {
        this.a = a;
        this.b = b;
        for (int j = 0; j < b.size(); j++) {
            b2j.computeIfAbsent(b.get(j), k -> new ArrayList<>()).add(j);
        }
    }

    static List<Op> opcodes(List<String> a, List<String> b) {
        SequenceDiff d = new SequenceDiff(a, b);
        List<Op> out = new ArrayList<>();
        int i = 0;
        int j = 0;
        for (Block m : d.matchingBlocks()) {
            String tag = "";
            if (i < m.a() && j < m.b()) {
                tag = "replace";
            } else if (i < m.a()) {
                tag = "delete";
            } else if (j < m.b()) {
                tag = "insert";
            }
            if (!tag.isEmpty()) {
                out.add(new Op(tag, i, m.a(), j, m.b()));
            }
            i = m.a() + m.size();
            j = m.b() + m.size();
            if (m.size() > 0) {
                out.add(new Op("equal", m.a(), i, m.b(), j));
            }
        }
        return out;
    }

    private Block longestMatch(int alo, int ahi, int blo, int bhi) {
        int bestI = alo;
        int bestJ = blo;
        int bestSize = 0;
        Map<Integer, Integer> j2len = new HashMap<>();
        for (int i = alo; i < ahi; i++) {
            Map<Integer, Integer> newJ2len = new HashMap<>();
            for (int j : b2j.getOrDefault(a.get(i), List.of())) {
                if (j < blo) {
                    continue;
                }
                if (j >= bhi) {
                    break;
                }
                int k = j2len.getOrDefault(j - 1, 0) + 1;
                newJ2len.put(j, k);
                if (k > bestSize) {
                    bestI = i - k + 1;
                    bestJ = j - k + 1;
                    bestSize = k;
                }
            }
            j2len = newJ2len;
        }
        // không có từ "rác" nên khối tìm được đã tối đa; vòng mở rộng của difflib chỉ giữ để cùng hành vi
        while (bestI > alo && bestJ > blo && a.get(bestI - 1).equals(b.get(bestJ - 1))) {
            bestI--;
            bestJ--;
            bestSize++;
        }
        while (bestI + bestSize < ahi && bestJ + bestSize < bhi && a.get(bestI + bestSize).equals(b.get(bestJ + bestSize))) {
            bestSize++;
        }
        return new Block(bestI, bestJ, bestSize);
    }

    private List<Block> matchingBlocks() {
        int la = a.size();
        int lb = b.size();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.push(new int[]{0, la, 0, lb});
        List<Block> found = new ArrayList<>();
        while (!queue.isEmpty()) {
            int[] q = queue.pop();
            Block m = longestMatch(q[0], q[1], q[2], q[3]);
            if (m.size() > 0) {
                found.add(m);
                if (q[0] < m.a() && q[2] < m.b()) {
                    queue.push(new int[]{q[0], m.a(), q[2], m.b()});
                }
                if (m.a() + m.size() < q[1] && m.b() + m.size() < q[3]) {
                    queue.push(new int[]{m.a() + m.size(), q[1], m.b() + m.size(), q[3]});
                }
            }
        }
        found.sort(Comparator.comparingInt(Block::a).thenComparingInt(Block::b).thenComparingInt(Block::size));

        List<Block> merged = new ArrayList<>();   // gộp các khối liền kề
        int i1 = 0;
        int j1 = 0;
        int k1 = 0;
        for (Block m : found) {
            if (i1 + k1 == m.a() && j1 + k1 == m.b()) {
                k1 += m.size();
            } else {
                if (k1 > 0) {
                    merged.add(new Block(i1, j1, k1));
                }
                i1 = m.a();
                j1 = m.b();
                k1 = m.size();
            }
        }
        if (k1 > 0) {
            merged.add(new Block(i1, j1, k1));
        }
        merged.add(new Block(la, lb, 0));
        return merged;
    }
}
