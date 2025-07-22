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

        Path inputPath = Paths.get(args[0]);
        Path outputPath = Paths.get("output.txt");

        try {
            long startTime = System.currentTimeMillis();

            Map<String, Integer> valueFrequencies = new HashMap<>();
            countValueFrequencies(inputPath, valueFrequencies);
            valueFrequencies.entrySet().removeIf(entry -> entry.getValue() <= 1);

            List<List<String>> groupedRows = collectRowData(inputPath, valueFrequencies);
            writeGroupedRowsToFile(outputPath, groupedRows);

            long endTime = System.currentTimeMillis();
            System.out.println("Групп с более чем одним элементом: " + groupedRows.size());
            System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
            System.out.println("Результат записан в: " + outputPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("Ошибка при работе с файлом: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // Шаг 1: Подсчёт количества вхождений уникальных значений по колонкам
    private static void countValueFrequencies(Path inputPath, Map<String, Integer> valueFrequencies) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseFileLine(line);
                if (parts == null){
                    continue;
                }

                for (int i = 0; i < parts.length; i++) {
                    String value = parts[i];
                    if (!value.isEmpty()) {
                        String key = i + "#" + value;
                        valueFrequencies.put(key, valueFrequencies.getOrDefault(key, 0) + 1);
                    }
                }
            }
        }
    }

    // Шаг 2: Сбор трок, принадлежащих потенциальным группам. Формирование групп.
    private static List<List<String>> collectRowData(Path inputPath, Map<String, Integer> valueFrequencies) throws IOException {
        List<String> filteredLines = new ArrayList<>();
        Map<String, List<Integer>> columnValueMap = new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(inputPath)) {
            String line;
            int rowIndex = 0;

            while ((line = reader.readLine()) != null) {
                String[] parts = parseFileLine(line);
                if (parts == null){
                    continue;
                }

                boolean isRelevant = false;

                for (int i = 0; i < parts.length; i++) {
                    String value = parts[i];
                    if (value.isEmpty()){
                        continue;
                    }

                    String key = i + "#" + value;
                    if (valueFrequencies.containsKey(key)) {
                        columnValueMap.computeIfAbsent(key, k -> new ArrayList<>()).add(rowIndex);
                        isRelevant = true;
                    }
                }

                if (isRelevant) {
                    filteredLines.add(line);
                    rowIndex++;
                }
            }
        }

        int totalRows = filteredLines.size();
        UnionFind uf = new UnionFind(totalRows);

        for (List<Integer> indices : columnValueMap.values()) {
            for (int i = 1; i < indices.size(); i++) {
                uf.union(indices.get(0), indices.get(i));
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < totalRows; i++) {
            int root = uf.find(i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(i);
        }

        List<List<String>> resultGroups = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() > 1) {
                List<String> lines = new ArrayList<>();
                for (int index : group) lines.add(filteredLines.get(index));
                resultGroups.add(lines);
            }
        }

        resultGroups.sort((a, b) -> b.size() - a.size());
        return resultGroups;
    }

    // Шаг 3: Запись результата в файл
    private static void writeGroupedRowsToFile(Path outputPath, List<List<String>> groupedRows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("Найдено групп с более чем одним элементом: " + groupedRows.size());
            writer.newLine();
            writer.newLine();

            for (int i = 0; i < groupedRows.size(); i++) {
                writer.write("Группа " + (i + 1));
                writer.newLine();

                for (String line : groupedRows.get(i)) {
                    writer.write(line);
                    writer.newLine();
                }

                writer.newLine();
            }
        }
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
