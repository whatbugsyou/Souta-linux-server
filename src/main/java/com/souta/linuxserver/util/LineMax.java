package com.souta.linuxserver.util;


import java.util.TreeSet;

public class LineMax {
    int max = 1;
    TreeSet<Integer> treeSet = new TreeSet<>();

    public void add(int num) {
        treeSet.add(num);
    }

    public int getMax() {
        if (!treeSet.isEmpty()) {
            int pre = 0;
            for (Integer integer : treeSet) {
                int next = integer;
                if (next - pre > 1) {
                    max = pre + 1;
                    return max;
                }
                pre = next;
            }
            max = pre + 1;
        }
        return max;
    }

    public void setTreeSet(TreeSet<Integer> treeSet) {
        this.treeSet = treeSet;
    }
}
