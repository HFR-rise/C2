package com.example.estimateserver.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Component
public class CustomDateDeserializer extends JsonDeserializer<Date> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        String dateStr = parser.getText();

        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }

        try {
            LocalDateTime ldt = LocalDateTime.parse(dateStr, FORMATTER);
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IOException("Cannot parse date: '" + dateStr + "'", e);
        }
    }
}