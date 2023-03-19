package de.unibamberg.dsam.group6.prost.service;

import com.google.gson.JsonObject;
import de.unibamberg.dsam.group6.prost.entity.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    @Value("${prost.cfg.bi-function-url}")
    private String biFunctionUrl;

    public void sendStats(Order order) {
        this.doPost(order.getStats());
    }

    private void doPost(JsonObject body) {
        RestTemplate restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        restTemplate.postForObject(this.biFunctionUrl, request, Object.class);
    }
}
