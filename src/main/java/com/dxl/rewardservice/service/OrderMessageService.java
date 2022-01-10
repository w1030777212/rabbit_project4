package com.dxl.rewardservice.service;

import com.dxl.rewardservice.dao.RewardMapper;
import com.dxl.rewardservice.dto.OrderMessageDTO;
import com.dxl.rewardservice.enumoperation.RewardStatus;
import com.dxl.rewardservice.po.RewardPO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

/**
 * @author : dxl
 * @version: 2022/1/10  13:58
 */
@Service
@Slf4j
public class OrderMessageService {
    @Autowired
    RewardMapper rewardMapper;

    @Async
    public void handleMessage() throws IOException, TimeoutException, InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        try(Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel()){
            channel.queueDeclare(
                    "queue.reward",
                    true,
                    false,
                    false,
                    null
            );
            channel.queueBind("queue.reward","exchange.order.reward", "key.reward");
            channel.basicConsume("queue.reward",true, deliverCallback, consumerTag -> {});
            while (true){
                Thread.sleep(100000);
            }
        }
    }

    DeliverCallback deliverCallback = ((consumerTag, message) -> {
        final ObjectMapper objectMapper = new ObjectMapper();
        final OrderMessageDTO orderMessageDTO = objectMapper.readValue(message.getBody(), OrderMessageDTO.class);
        final RewardPO rewardPO = new RewardPO();
        rewardPO.setAmount(orderMessageDTO.getPrice());
        rewardPO.setOrderId(orderMessageDTO.getOrderId());
        rewardPO.setStatus(RewardStatus.SUCCESS);
        rewardPO.setDate(new Date());
        rewardMapper.insert(rewardPO);

        orderMessageDTO.setRewardId(rewardPO.getId());
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("localhost");
        try(Connection connection = connectionFactory.newConnection();
            Channel channel = connection.createChannel()){
            final String messageToSend = objectMapper.writeValueAsString(orderMessageDTO);
            channel.basicPublish("exchange.order.reward",
                    "key.order",
                    null,
                    messageToSend.getBytes());
        }catch (Exception e){
            log.error(e.getMessage());
        }
    });
}
