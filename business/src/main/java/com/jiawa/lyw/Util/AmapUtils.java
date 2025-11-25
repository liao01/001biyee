package com.jiawa.lyw.Util;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class AmapUtils {

    @Value("${amap.key}")
    private String amapKey;

    private static final String GEO_URL = "https://restapi.amap.com/v3/geocode/geo";

    public JSONObject getLocationByAddress(String address) {
        RestTemplate restTemplate = new RestTemplate();
        String url = GEO_URL + "?address=" + address + "&key=" + amapKey;
        String result = restTemplate.getForObject(url, String.class);

        JSONObject json = JSONObject.parseObject(result);
        if ("1".equals(json.getString("status"))) {
            JSONObject geocode = json.getJSONArray("geocodes").getJSONObject(0);
            String location = geocode.getString("location");
            String[] lnglat = location.split(",");
            JSONObject data = new JSONObject();
            data.put("longitude", lnglat[0]);
            data.put("latitude", lnglat[1]);
            data.put("formatted_address", geocode.getString("formatted_address"));
            data.put("province", geocode.getString("province"));
            data.put("city", geocode.getString("city"));
            data.put("district", geocode.getString("district"));
            return data;
        }
        return null;
    }
}
