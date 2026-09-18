package com.example.estimateserver.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Component
public class CustomDateSerializer extends JsonSerializer<Date> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void serialize(Date date, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (date == null) {
            gen.writeNull();
            return;
        }

        String formatted = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(FORMATTER);

        gen.writeString(formatted);
    }
}