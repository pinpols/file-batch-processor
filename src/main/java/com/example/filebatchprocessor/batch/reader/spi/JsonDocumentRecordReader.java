package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import org.springframework.core.io.Resource;

/** JSON 顶层对象数组的流式读取(Jackson streaming,不全量物化)。 */
public class JsonDocumentRecordReader implements DocumentRecordReader {

    private final ObjectMapper objectMapper;
    private InputStream in;
    private JsonParser parser;

    public JsonDocumentRecordReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Iterator<FileRecord> open(Resource resource) throws Exception {
        this.in = resource.getInputStream();
        this.parser = objectMapper.getFactory().createParser(in);
        JsonToken first = parser.nextToken();
        if (first != JsonToken.START_ARRAY) {
            throw new IllegalArgumentException("JSON import expects a top-level array");
        }
        return new Iterator<>() {
            private Boolean hasNextCached;

            @Override
            public boolean hasNext() {
                if (hasNextCached != null) {
                    return hasNextCached;
                }
                try {
                    hasNextCached = parser.nextToken() == JsonToken.START_OBJECT;
                    return hasNextCached;
                } catch (Exception e) {
                    throw new RuntimeException("JSON parse error", e);
                }
            }

            @Override
            public FileRecord next() {
                if (hasNextCached == null) {
                    hasNext();
                }
                if (!Boolean.TRUE.equals(hasNextCached)) {
                    throw new NoSuchElementException();
                }
                hasNextCached = null;
                try {
                    JsonNode node = objectMapper.readTree(parser);
                    FileRecord r = new FileRecord();
                    if (node.hasNonNull("id")) {
                        r.setId(node.get("id").asLong());
                    }
                    if (node.hasNonNull("name")) {
                        r.setName(node.get("name").asText());
                    }
                    if (node.hasNonNull("description")) {
                        r.setDescription(node.get("description").asText());
                    }
                    return r;
                } catch (Exception e) {
                    throw new RuntimeException("JSON record parse error", e);
                }
            }
        };
    }

    @Override
    public void close() throws java.io.IOException {
        if (parser != null) {
            parser.close();
        }
        if (in != null) {
            in.close();
        }
    }
}
