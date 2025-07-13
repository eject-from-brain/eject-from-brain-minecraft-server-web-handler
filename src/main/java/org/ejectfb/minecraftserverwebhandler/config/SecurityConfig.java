package org.ejectfb.minecraftserverwebhandler.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private ServerProperties serverProperties;
    private InMemoryUserDetailsManager userDetailsManager;

    @Value("${security.user.username}")
    private String username;

    @Value("${security.user.password}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("USER")
                .build();

        serverProperties.getSecurity().setUsername(username);
        serverProperties.getSecurity().setPassword(password);

        this.userDetailsManager = new InMemoryUserDetailsManager(user);
        return this.userDetailsManager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public void updateCredentials(String newUsername, String newPassword, HttpServletRequest request) {
        String currentUsername = serverProperties.getSecurity().getUsername();
        String currentPassword = serverProperties.getSecurity().getPassword();

        boolean credentialsChanged = !newUsername.equals(currentUsername)
                || !newPassword.equals(currentPassword);

        if (!credentialsChanged) {
            return;
        }

        if (userDetailsManager.userExists(currentUsername)) {
            userDetailsManager.deleteUser(currentUsername);
        }

        serverProperties.getSecurity().setUsername(newUsername);
        serverProperties.getSecurity().setPassword(newPassword);

        UserDetails user = User.builder()
                .username(newUsername)
                .password(passwordEncoder().encode(newPassword))
                .roles("USER")
                .build();

        userDetailsManager.createUser(user);

        if (request != null) {
            new SecurityContextLogoutHandler().logout(
                    request,
                    null,
                    SecurityContextHolder.getContext().getAuthentication()
            );
        }
    }
}