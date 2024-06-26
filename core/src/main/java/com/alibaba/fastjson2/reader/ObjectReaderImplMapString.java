package com.alibaba.fastjson2.reader;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONReader;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ObjectReaderImplMapString
        extends ObjectReaderImplMapTyped {
    public ObjectReaderImplMapString(Class mapType, Class instanceType, long features) {
        super(mapType, instanceType, null, String.class, features, null);
    }

    @Override
    public Object readObject(JSONReader jsonReader, Type fieldType, Object fieldName, long features) {
        if (jsonReader.jsonb) {
            return this.readJSONBObject(jsonReader, fieldType, fieldName, features);
        }

        boolean match = jsonReader.nextIfObjectStart();
        if (!match) {
            if (jsonReader.current() == '[') {
                jsonReader.next();
                if (jsonReader.current() == '{') {
                    Object arrayItem = readObject(jsonReader, String.class, fieldName, features);
                    if (jsonReader.nextIfArrayEnd()) {
                        jsonReader.nextIfComma();
                        return arrayItem;
                    }
                }
                throw new JSONException(jsonReader.info("expect '{', but '['"));
            }

            if (jsonReader.nextIfNullOrEmptyString() || jsonReader.nextIfMatchIdent('"', 'n', 'u', 'l', 'l', '"')) {
                return null;
            }
        }

        JSONReader.Context context = jsonReader.getContext();
        Map<String, Object> object
                = instanceType == HashMap.class
                ? new HashMap<>()
                : (Map) createInstance(context.getFeatures() | features);
        long contextFeatures = features | context.getFeatures();

        for (int i = 0; ; ++i) {
            if (jsonReader.nextIfObjectEnd()) {
                break;
            }

            String name = jsonReader.readFieldName();

            if (multiValue && jsonReader.nextIfArrayStart()) {
                List list = new JSONArray();
                while (!jsonReader.nextIfArrayEnd()) {
                    String value = jsonReader.readString();
                    list.add(value);
                }
                object.put(name, list);
                continue;
            }

            String value = jsonReader.readString();
            if (i == 0
                    && (contextFeatures & JSONReader.Feature.SupportAutoType.mask) != 0
                    && name.equals(getTypeKey())) {
                continue;
            }

            if (value == null && (contextFeatures & JSONReader.Feature.IgnoreNullPropertyValue.mask) != 0) {
                continue;
            }

            Object origin = object.put(name, value);
            if (origin != null) {
                if ((contextFeatures & JSONReader.Feature.DuplicateKeyValueAsArray.mask) != 0) {
                    if (origin instanceof Collection) {
                        ((Collection) origin).add(value);
                        object.put(name, origin);
                    } else {
                        JSONArray array = JSONArray.of(origin, value);
                        object.put(name, array);
                    }
                }
            }
        }

        jsonReader.nextIfMatch(',');

        return object;
    }
}
