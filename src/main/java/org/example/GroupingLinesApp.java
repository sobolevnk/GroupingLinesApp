package org.example;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GroupingLinesApp {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -Xmx1G -jar GroupingLinesApp-1.0.jar <input_file>");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            Path inputPath = Paths.get(args[0]);
            Path outputPath = Paths.get("output.txt");

            Map<String, Integer> valueFrequencies = new HashMap<>();
            countValueFrequencies(inputPath, valueFrequencies);

            valueFrequencies.entrySet().removeIf(entry -> entry.getValue() <= 1);

            int lineSeparatorLength = detectLineSeparatorLength(inputPath);
            Map<Integer, Long> rowOffsets = new HashMap<>();
            Map<String, List<Integer>> columnValueToRowIndexes = new HashMap<>();
            int totalRows = collectRowData(inputPath, valueFrequencies, columnValueToRowIndexes, rowOffsets, lineSeparatorLength);

            List<List<Integer>> groupedRowsList = buildGroups(totalRows, columnValueToRowIndexes);
            writeGroupedRowsToFile(inputPath, outputPath, groupedRowsList, rowOffsets);

            long endTime = System.currentTimeMillis();
            System.out.println("Групп с более чем одним элементом: " + groupedRowsList.size());
            System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
            System.out.println("Результат записан в: " + outputPath.toAbsolutePath());
        }
        catch (IOException e) {
            System.err.println("Ошибка при работе с файлом: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Шаг 1: Подсчёт количества вхождений уникальных значений по колонкам
    private static void countValueFrequencies(Path inputPath, Map<String, Integer> frequencies) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseFileLine(line);
                if (parts == null) continue;

                for (int i = 0; i < parts.length; i++) {
                    String value = parts[i];
                    if (!value.isEmpty()) {
                        String key = i + "#" + value;
                        frequencies.put(key, frequencies.getOrDefault(key, 0) + 1);
                    }
                }
            }
        }
    }

    // Шаг 2: Сбор оффсетов и строк, принадлежащих потенциальным группам
    private static int collectRowData(
            Path inputPath,
            Map<String, Integer> frequencies,
            Map<String, List<Integer>> columnValueToRowIndexes,
            Map<Integer, Long> rowOffsets,
            int separatorLength
    ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            long offset = 0;
            int rowIndex = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = parseFileLine(line);
                if (parts == null) {
                    offset += line.getBytes().length + separatorLength;
                    continue;
                }

                boolean isRowRelevant = false;
                for (int i = 0; i < parts.length; i++) {
                    String value = parts[i];
                    if (!value.isEmpty()) {
                        String key = i + "#" + value;
                        if (frequencies.containsKey(key)) {
                            columnValueToRowIndexes.computeIfAbsent(key, k -> new ArrayList<>()).add(rowIndex);
                            isRowRelevant = true;
                        }
                    }
                }

                if (isRowRelevant) {
                    rowOffsets.put(rowIndex, offset);
                    rowIndex++;
                }

                offset += line.getBytes().length + separatorLength;
            }

            return rowIndex;
        }
    }

    // Шаг 3: Построение групп с помощью Union-Find
    private static List<List<Integer>> buildGroups(int totalRows, Map<String, List<Integer>> columnValueToRowIndexes) {
        UnionFind uf = new UnionFind(totalRows);
        for (List<Integer> rowIndexes : columnValueToRowIndexes.values()) {
            for (int i = 1; i < rowIndexes.size(); i++) {
                uf.union(rowIndexes.get(0), rowIndexes.get(i));
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < totalRows; i++) {
            int root = uf.find(i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        List<List<Integer>> result = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() > 1) {
                result.add(group);
            }
        }

        result.sort((a, b) -> b.size() - a.size());
        return result;
    }

    // Шаг 4: Запись результата в файл
    private static void writeGroupedRowsToFile(
            Path inputPath,
            Path outputPath,
            List<List<Integer>> groupedRows,
            Map<Integer, Long> rowOffsets
    ) throws IOException {
        try (
                RandomAccessFile raf = new RandomAccessFile(inputPath.toFile(), "r");
                BufferedWriter writer = Files.newBufferedWriter(outputPath)
        ) {
            writer.write("Найдено групп с более чем одним элементом: " + groupedRows.size());
            writer.newLine();
            writer.newLine();

            for (int i = 0; i < groupedRows.size(); i++) {
                writer.write("Группа " + (i + 1));
                writer.newLine();
                for (int rowIndex : groupedRows.get(i)) {
                    long pos = rowOffsets.get(rowIndex);
                    raf.seek(pos);
                    String line = raf.readLine();
                    if (line != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                writer.newLine();
            }
        }
    }

    // Определение длины разделителя строк
    private static int detectLineSeparatorLength(Path inputPath) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(inputPath))) {
            int prev = -1, curr;
            while ((curr = in.read()) != -1) {
                if (curr == '\n') {
                    return (prev == '\r') ? 2 : 1;
                }
                prev = curr;
            }
        }
        return System.lineSeparator().getBytes().length;
    }

    // Разбор строки: элементы заключены в кавычки и разделены ;
    private static String[] parseFileLine(String line) {
        List<String> tokens = new ArrayList<>();
        int i = 0, n = line.length();

        while (i < n) {
            if (line.charAt(i) != '"') return null;
            int j = i + 1;
            while (j < n && line.charAt(j) != '"') j++;
            if (j >= n) return null;
            tokens.add(line.substring(i + 1, j));
            i = j + 1;
            if (i < n && line.charAt(i) == ';') i++;
        }

        return tokens.toArray(new String[0]);
    }

    // Класс Union-Find (Disjoint Set Union)
    static class UnionFind {
        int[] parent;

        UnionFind(int size) {
            parent = new int[size];
            for (int i = 0; i < size; i++) parent[i] = i;
        }

        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA != rootB) parent[rootB] = rootA;
        }
    }
}
