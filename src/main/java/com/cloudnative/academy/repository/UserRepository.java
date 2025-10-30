package com.cloudnative.academy.repository;

import com.cloudnative.academy.domain.User;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private List<User> users = new ArrayList<>();

    // Método para guardar usuarios
    public User save(User user) {
        users.add(user);
        return user;
    }

    // Método para buscar usuarios por nombre
    public Optional<User> findByName(String name) {
        return users.stream().filter(user -> user.getName().equals(name)).findFirst();
    }

    // Método para eliminar usuarios por nombre
    public void deleteByName(String name) {
        users.removeIf(user -> user.getName().equals(name));
    }

    // Método para obtener todos los usuarios
    public List<User> findAll() {
        return users;
    }
}
