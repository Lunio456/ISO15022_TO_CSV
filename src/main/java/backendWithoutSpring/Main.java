package backendWithoutSpring;

import com.prowidesoftware.swift.io.parser.SwiftParser;
import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {

        Path filePath = Path.of("src/main/resources/input.txt");

        String fileContent = Files.readString(filePath);
        String[] records = fileContent.split("(?<=})\\$(?=\\{)");
        List<Map<String, String>> data = new ArrayList<>();
        List<String> fieldNames = new ArrayList<>();
        Map<String, String> recordMap;


        for (String record : records) {
            SwiftParser parser = new SwiftParser(record);
            SwiftMessage mt = parser.message();
            recordMap = getRecord(mt);
            data.add(recordMap);
            mergeLists(fieldNames, new ArrayList<>(recordMap.keySet()));
            System.out.println("---");
        }

        generateCSVFile(data, fieldNames);
    }

    public static Map<String, String> getRecord(SwiftMessage mt) {
        SwiftTagListBlock tagsBlock = (SwiftTagListBlock) mt.getBlock(4);

        String section = "";
        int numberOfOptions = 0;
        String value;
        String name;
        int lineEndIndex;
        Map<String, String> recordMap = new LinkedHashMap<>();

        for (int i = 0; i < tagsBlock.getTags().size(); i++) {
            value = tagsBlock.getTags().get(i).getValue();
            name = tagsBlock.getTags().get(i).getName();
            switch (name) {
                case "16R" -> {
                    section = section + value + ".";
                    if (value.equals("CAOPTN")) {
                        numberOfOptions++;
                        section = section + numberOfOptions + ".";
                    }
                }
                case "16S" -> {
                    if (value.equals("CAOPTN")) {
                        section = section.replace(value + "." + numberOfOptions + ".", "");
                    } else {
                        section = section.replace(value + ".", "");
                    }
                }
                case "23G" -> addLineToMap(recordMap, section + name + "//" + value);
                case "35B" -> {
                    lineEndIndex = value.indexOf("\n");
                    if (lineEndIndex != -1) {
                        addLineToMap(recordMap, section + name + "_" +
                                value.substring(0, lineEndIndex).replace(" ", "//"));
                        addLineToMap(recordMap, section + "Description" +
                                value.substring(lineEndIndex + 1).replace("\r\n", " "));
                    } else {
                        addLineToMap(recordMap, section + name + "_" + value.replace(" ", "//"));
                    }
                }
                default -> addLineToMap(recordMap, section +
                        Main.formatTag(tagsBlock.getTags().get(i)).replace("\r\n", " "));
            }
        }
        return recordMap;
    }

    public static void addLineToMap(Map<String, String> recordMap, String line) {
        if (!line.contains("//")) {
            System.out.println("Cannot be processed:" + line);
            return;
        }
        String key = line.substring(0, line.indexOf("//"));
        String value = line.substring(line.indexOf("//") + 2);
        recordMap.put(key, value);
        System.out.println(line);
    }

    public static String formatTag(Tag tag) {
        String value = tag.getValue();

        if (value.startsWith(":")) {
            value = value.substring(1); // Remove ':' if present at the start
        }

        return tag.getName() + "_" + value;
    }

    public static void mergeLists(List<String> list1, List<String> list2) {
        for (String s : list2) {
            int index = 0;
            String section;
            int fromIndex = s.length() - 1;
            if (!list1.contains(s)) {
                while (index == 0) {
                    if (fromIndex == -1) {
                        break;
                    }
                    section = s.substring(0, s.lastIndexOf( ".", fromIndex));
                    index = getInsertionIndex(list1, section);
                    fromIndex = s.lastIndexOf( ".", fromIndex-1);
                }
                if (index != 0) {
                    list1.add(index + 1, s);
                } else {
                    list1.add(s);
                }
            }
        }
    }

    public static int getInsertionIndex(List<String> list, String s) {
        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).substring(0,list.get(i).lastIndexOf(".")).equals(s)) {
                index = i;
            }
        }
        return index;
    }

    public static void generateCSVFile(List<Map<String, String>> data, List<String> fieldNames) {
        // Generate the CSV file
        try (FileWriter csvWriter = new FileWriter("output.csv")) {
            // Write the header row
            csvWriter.append(String.join(";", fieldNames));
            csvWriter.append("\n");

            // Write data rows
            for (Map<String, String> record : data) {
                for (String field : fieldNames) {
                    csvWriter.append(record.getOrDefault(field, "")); // Default empty if no value
                    csvWriter.append(";");
                }
                csvWriter.append("\n");
            }

            System.out.println("CSV file 'output.csv' has been generated successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}