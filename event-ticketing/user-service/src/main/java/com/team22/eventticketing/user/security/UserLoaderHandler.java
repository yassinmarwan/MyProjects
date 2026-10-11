package com.team22.eventticketing.user.security;

import com.team22.eventticketing.user.entity.User;
import com.team22.eventticketing.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

public class UserLoaderHandler extends AuthHandler {

    private final UserRepository userRepository;

    public UserLoaderHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void handle(AuthContext ctx) {
        Claims claims = ctx.getClaims();
        String email = claims.getSubject();

        if (email == null) {
            throw new BadCredentialsException("Token subject (email) is missing");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new BadCredentialsException("User not found");
        }

        User user = userOpt.get();
        ctx.setUserEmail(user.getEmail());
        ctx.setUserRole(user.getRole().name());
        ctx.setUserId(user.getId());

        passToNext(ctx);
    }
}