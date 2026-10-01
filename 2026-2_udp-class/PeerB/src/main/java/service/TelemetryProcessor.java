package service;

import model.TelemetryData;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Procesador de mensajes del protocolo de telemetría IoT sobre UDP.
 * 
 * Formato de mensajes recibidos:
 *   - Registro de telemetría: "DEVICE_ID;SENSOR_TYPE;VALUE" (ej. "sensor-01;TEMP;25.5")
 *   - Consulta de estado:      "STATUS;DEVICE_ID" (ej. "STATUS;sensor-01")
 */
public class TelemetryProcessor {

    private static final String INVALID_FORMAT = "ERROR;INVALID_FORMAT";

    private final Map<String, TelemetryData> lastReadings = new ConcurrentHashMap<>();

    /**
     * Procesa un mensaje de texto recibido por UDP y devuelve la respuesta
     * correspondiente según las reglas del protocolo de telemetría.
     * 
     * @param rawMessage Mensaje en texto plano recibido en el datagrama UDP.
     * @return Respuesta que será enviada de regreso al cliente emisor.
     */
    public String process(String rawMessage) {
        // Paso 1.1: validar nulo / vacío
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return INVALID_FORMAT;
        }

        // Paso 1.2: separar por ";" (límite -1 conserva campos vacíos al final)
        String[] parts = rawMessage.trim().split(";", -1);
        if (parts.length == 0) {
            return INVALID_FORMAT;
        }

        // Paso 1.3: consulta de estado
        if (parts[0].trim().equalsIgnoreCase("STATUS")) {
            if (parts.length != 2 || parts[1].trim().isEmpty()) {
                return INVALID_FORMAT;
            }
            String queriedId = parts[1].trim();
            TelemetryData data = lastReadings.get(queriedId);
            if (data == null) {
                return "ERROR;DEVICE_NOT_FOUND";
            }
            return "STATUS_OK;" + data.getDeviceId() + ";" + data.getSensorType() + ";" + data.getValue();
        }

        // Paso 1.4: validar formato de telemetría
        if (parts.length != 3) {
            return INVALID_FORMAT;
        }
        String deviceId = parts[0].trim();
        String sensorType = parts[1].trim();
        String valueStr = parts[2].trim();
        if (deviceId.isEmpty() || sensorType.isEmpty() || valueStr.isEmpty()) {
            return INVALID_FORMAT;
        }

        double value;
        try {
            value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            return INVALID_FORMAT;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return INVALID_FORMAT;
        }

        // Paso 1.6 (validación del tipo) + 1.5 (almacenamiento de lecturas válidas)
        String type = sensorType.toUpperCase();
        String response;
        switch (type) {
            case "TEMP":
                if (value > 40.0) {
                    response = "ALERT;HIGH_TEMPERATURE;" + value;
                } else if (value < 0.0) {
                    response = "ALERT;FREEZING_TEMPERATURE;" + value;
                } else {
                    response = "OK;TEMP_RECORDED;" + value;
                }
                break;
            case "HUMIDITY":
                if (value > 90.0) {
                    response = "ALERT;HIGH_HUMIDITY;" + value;
                } else if (value < 20.0) {
                    response = "ALERT;LOW_HUMIDITY;" + value;
                } else {
                    response = "OK;HUMIDITY_RECORDED;" + value;
                }
                break;
            case "BATTERY":
                if (value < 20.0) {
                    response = "ALERT;LOW_BATTERY;" + value;
                } else {
                    response = "OK;BATTERY_OK;" + value;
                }
                break;
            default:
                return "ERROR;UNKNOWN_SENSOR_TYPE";
        }

        lastReadings.put(deviceId, new TelemetryData(deviceId, type, value));
        return response;
    }

    public Map<String, TelemetryData> getLastReadings() {
        return lastReadings;
    }

    public void clear() {
        lastReadings.clear();
    }
}
