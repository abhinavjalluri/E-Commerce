package com.example.orderservice.controllers;

import com.example.orderservice.config.context.ContextHolder;
import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.exceptions.OrderControllerErrorHandling;
import com.example.orderservice.services.IOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderControllerTest {
  private IOrderService service;
  private MockMvc mvc;
  private static final String URL = "/api/v1/orders/users/me";

  @BeforeEach
  void setUp() {
    service = mock(IOrderService.class);
    mvc = MockMvcBuilders.standaloneSetup(new OrderController(service, mock(ContextHolder.class)))
      .setControllerAdvice(new OrderControllerErrorHandling()).build();
  }

  @Test
  void routes_ShouldExposeAllCrudOperations() throws Exception {
    var dto = OrderDto.builder().id(2L).userId(1L).build();
    when(service.addOne(any())).thenReturn(dto);
    when(service.getAll()).thenReturn(List.of(dto));
    when(service.getOne(2L)).thenReturn(dto);
    when(service.updateOne(eq(2L), any())).thenReturn(dto);
    String body = "{\"products\":[{\"productId\":1,\"quantity\":2}]}";

    mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
    mvc.perform(get(URL)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(2));
    mvc.perform(get(URL + "/2")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
    mvc.perform(put(URL + "/2").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
    mvc.perform(delete(URL + "/2")).andExpect(status().isNoContent()).andExpect(content().string(""));
    verify(service).deleteOne(2L);
  }

}
