package org.nagineco.doppel.core;

import java.util.*;

/** Bounded 2D A* for same-height, walkable Minecraft cells. */
public final class NaoGridPathfinder {
    private NaoGridPathfinder() {}
    public record Cell(int x, int z) {}
    @FunctionalInterface public interface Walkable { boolean test(int x, int z); }
    private record Node(Cell cell, int g, int f) {}
    private static int heuristic(Cell a, Cell b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
    }
    public static List<Cell> find(Cell start, Cell goal, Walkable walkable, int radius) {
        Objects.requireNonNull(start); Objects.requireNonNull(goal); Objects.requireNonNull(walkable);
        if (radius < 1 || radius > 32) throw new IllegalArgumentException("radius");
        if (heuristic(start, goal) > radius * 2 || !walkable.test(start.x(), start.z()))
            return List.of();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::f)
            .thenComparingInt(Node::g).thenComparingInt(n -> n.cell().x())
            .thenComparingInt(n -> n.cell().z()));
        Map<Cell, Integer> distance = new HashMap<>();
        Map<Cell, Cell> parent = new HashMap<>();
        distance.put(start, 0);
        open.add(new Node(start, 0, heuristic(start, goal)));
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 4096) {
            Node node = open.poll();
            if (node.g() != distance.getOrDefault(node.cell(), Integer.MAX_VALUE)) continue;
            if (node.cell().equals(goal)) {
                List<Cell> path = new ArrayList<>();
                Cell cursor = goal;
                while (!cursor.equals(start)) {
                    path.add(cursor);
                    cursor = parent.get(cursor);
                }
                Collections.reverse(path);
                return List.copyOf(path);
            }
            int[][] dirs = {{1,0},{0,1},{-1,0},{0,-1}};
            for (int[] d : dirs) {
                Cell next = new Cell(node.cell().x()+d[0], node.cell().z()+d[1]);
                if (Math.abs(next.x()-start.x()) > radius ||
                    Math.abs(next.z()-start.z()) > radius ||
                    !walkable.test(next.x(), next.z())) continue;
                int g = node.g()+1;
                if (g >= distance.getOrDefault(next, Integer.MAX_VALUE)) continue;
                distance.put(next, g);
                parent.put(next, node.cell());
                open.add(new Node(next, g, g+heuristic(next, goal)));
            }
        }
        return List.of();
    }
}
