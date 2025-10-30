package com.cloudnative.academy.api;

import com.cloudnative.academy.domain.User;
import com.cloudnative.academy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getUsers() {
        // Ejemplo de código inseguro con inyección SQL
        String username = "testUser"; // Supón que esto proviene de un parámetro o input del usuario
        String query = "SELECT * FROM users WHERE username = '" + username + "'"; // Vulnerable a inyección SQL

        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "user", "password");
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                // Procesar los resultados y convertirlos a objetos User (simplificado)
                String userName = rs.getString("username");
                // ... otras columnas
            }

            rs.close();
            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
}
