package com.modbusweb.service.bacnet;

import com.modbusweb.domain.BacnetObjectType;
import com.modbusweb.domain.ModbusDataType;
import com.modbusweb.domain.entity.ModbusPoint;
import com.modbusweb.service.ModbusValueCodec;

/**
 * Extension point between the Modbus point model and a BACnet object model.
 *
 * <p>Implementations can be registered as Spring beans (for example an implementation that maps
 * STRING points onto a vendor-specific object) and selected by {@code DefaultBacnetMappingService}.
 * The default implementation maps boolean points to Binary objects and numeric points to Analog
 * objects.
 */
public interface BacnetValueAdapter {

    /**
     * Suggest a BACnet object type for a point, or {@code null} when no safe standard mapping
     * exists.
     */
    BacnetObjectType suggestObjectType(ModbusPoint point);

    /**
     * Convert a decoded Modbus value to a BACnet present-value and its BACnet application data type
     * name.
     */
    AdapterResult toPresentValue(
            ModbusPoint point, BacnetObjectType objectType, ModbusValueCodec.DecodedValue value);

    /** Returns true when a manually selected object type is compatible with the point. */
    boolean supports(ModbusPoint point, BacnetObjectType objectType);

    record AdapterResult(String presentValueType, Object presentValue) {}

    static BacnetValueAdapter defaultAdapter() {
        return new DefaultBacnetValueAdapter();
    }

    final class DefaultBacnetValueAdapter implements BacnetValueAdapter {

        @Override
        public BacnetObjectType suggestObjectType(ModbusPoint point) {
            if (point.getDataType() == ModbusDataType.BOOL) {
                return point.supportsWrite()
                        ? BacnetObjectType.BINARY_VALUE
                        : BacnetObjectType.BINARY_INPUT;
            }
            if (point.getDataType().isNumeric()) {
                return point.supportsWrite()
                        ? BacnetObjectType.ANALOG_VALUE
                        : BacnetObjectType.ANALOG_INPUT;
            }
            // STRING / HEX have no safe standard BACnet object representation.
            return null;
        }

        @Override
        public AdapterResult toPresentValue(
                ModbusPoint point,
                BacnetObjectType objectType,
                ModbusValueCodec.DecodedValue value) {
            if (objectType.getFamily() == BacnetObjectType.ValueFamily.BINARY) {
                boolean active = Boolean.TRUE.equals(value.booleanValue());
                return new AdapterResult("ENUMERATED", active ? "ACTIVE" : "INACTIVE");
            }
            if (objectType.getFamily() == BacnetObjectType.ValueFamily.MULTI_STATE) {
                int state = value.numericValue() == null ? 0 : value.numericValue().intValue();
                return new AdapterResult("UNSIGNED_INTEGER", Math.max(0, state));
            }
            double numeric = value.numericValue() == null ? 0.0d : value.numericValue();
            return new AdapterResult("REAL", numeric);
        }

        @Override
        public boolean supports(ModbusPoint point, BacnetObjectType objectType) {
            return switch (objectType.getFamily()) {
                case BINARY -> point.getDataType() == ModbusDataType.BOOL;
                case ANALOG, MULTI_STATE -> point.getDataType().isNumeric();
            };
        }
    }
}
