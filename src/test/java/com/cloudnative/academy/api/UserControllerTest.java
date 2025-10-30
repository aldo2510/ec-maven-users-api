package com.cloudnative.academy.api;

import com.cloudnative.academy.service.UserService;
import com.cloudnative.academy.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class UserControllerTest {

    @Mock
    private UserService userService; // Mock de UserService

    @InjectMocks
    private UserController userController; // UserController donde se inyectará el mock

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Inicializa los mocks
    }

    @Test
    void testGetUsers() {
        User user = new User(1L, "John Doe", "john.doe@example.com");
        when(userService.getAllUsers()).thenReturn(List.of(user)); // Simula el comportamiento de getAllUsers()

        // Aquí aseguramos que el controlador devuelve un ResponseEntity
        ResponseEntity<List<User>> response = userController.getUsers();

        // Comprobamos que el estado HTTP sea 200 OK
        assertEquals(200, response.getStatusCodeValue());

        // Comprobamos que la lista no esté vacía
        assertFalse(response.getBody().isEmpty());

        // Verificamos que el nombre del primer usuario sea "John Doe"
        assertEquals("John Doe", response.getBody().get(0).getName());
    }
}
