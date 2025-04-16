package backend;

import com.prowidesoftware.swift.io.parser.SwiftParser;
import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class FileProcessingController {

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InputStreamResource> processFile(@RequestParam("file") MultipartFile file) {
        try {

            String fileContent;
            // Read the uploaded file contents
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                fileContent = reader.lines().collect(Collectors.joining("\n"));

            }

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

            //System.out.println(fieldNames);
            ByteArrayOutputStream outputStream = generateCSVFile(data, fieldNames);


            // Wrap the output as a CSV file
            InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(outputStream.toByteArray()));
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=\"processed.csv\"");
            headers.add("Access-Control-Allow-Origin", "*"); // Allow all origins (adjust as needed)
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(outputStream.size())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(null);
        }
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
                        addLineToMap(recordMap, section + "Description/" +
                                value.substring(lineEndIndex + 1).replaceAll("\\r?\\n", " "));
                    } else {
                        addLineToMap(recordMap, section + name + "_" + value.replace(" ", "//"));
                    }
                }
                default -> addLineToMap(recordMap, section +
                        formatTag(tagsBlock.getTags().get(i)).replaceAll("\\r?\\n", " "));
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
        String value = line.substring(line.indexOf("//") + 2).replaceAll(",", ".");
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

    public static ByteArrayOutputStream generateCSVFile(List<Map<String, String>> data, List<String> fieldNames) {
        // Generate the CSV file into a ByteArrayOutputStream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // Generate the CSV file
        try (PrintWriter csvWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream))) {
            // Write the header row
            csvWriter.println(String.join(";", fieldNames));

            // Write data rows
            for (Map<String, String> record : data) {
                for (String field : fieldNames) {
                    csvWriter.print(record.getOrDefault(field, "")); // Default empty if no value
                    csvWriter.print(";");
                }
                csvWriter.println();
            }
            csvWriter.flush();
            System.out.println("CSV file 'output.csv' has been generated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream;
    }
}