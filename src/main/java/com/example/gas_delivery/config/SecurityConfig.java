package com.example.gas_delivery.config;

import com.example.gas_delivery.security.BotApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Segurança da API.
 *
 * <ul>
 *   <li><b>Painel / endpoints administrativos</b> ({@code /api/**}): exigem
 *       autenticação HTTP Basic. O usuário é configurado por variáveis de
 *       ambiente ({@code APP_ADMIN_USUARIO} / {@code APP_ADMIN_SENHA}).</li>
 *   <li><b>Endpoint do robô</b> ({@code POST /api/pedidos/via-bot}): liberado no
 *       filtro de autorização e protegido por chave compartilhada no
 *       {@link BotApiKeyFilter} (header {@code X-Api-Key}).</li>
 *   <li><b>CORS</b>: restrito às origens de {@code app.seguranca.origens-permitidas}
 *       (nada de {@code *}).</li>
 *   <li>Sessões stateless, CSRF desabilitado (API sem cookies).</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Senha padrão de desenvolvimento — nunca deve ir para produção. */
    static final String SENHA_PADRAO_DEV = "binho-admin";

    @Value("${app.seguranca.admin-usuario:admin}")
    private String adminUsuario;

    @Value("${app.seguranca.admin-senha:" + SENHA_PADRAO_DEV + "}")
    private String adminSenha;

    @Value("${app.seguranca.origens-permitidas:http://localhost:5173,http://localhost:8080}")
    private String origensPermitidas;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String senhaEfetiva = adminSenha;

        if (senhaEfetiva == null || senhaEfetiva.isBlank()) {
            // Nunca deixar o painel com senha vazia: senha vazia seria ACEITA no login.
            senhaEfetiva = gerarSenhaAleatoria();
            log.warn("""

                    ================================================================
                      SENHA GERADA AUTOMATICAMENTE (APP_ADMIN_SENHA não definida)
                        usuário: {}
                        senha:   {}
                      Esta senha MUDA a cada reinício. Defina APP_ADMIN_SENHA no .env
                      para ter uma senha fixa.
                    ================================================================""",
                    adminUsuario, senhaEfetiva);
        } else if (SENHA_PADRAO_DEV.equals(senhaEfetiva)) {
            log.warn("[SEGURANÇA] usando a SENHA PADRÃO de desenvolvimento. "
                    + "Defina APP_ADMIN_SENHA antes de publicar em produção.");
        }

        UserDetails admin = User.withUsername(adminUsuario)
                .password(encoder.encode(senhaEfetiva))
                .roles("ADMIN")
                .build();
        log.info("[SEGURANÇA] painel protegido — usuário '{}'.", adminUsuario);
        return new InMemoryUserDetailsManager(admin);
    }

    /** Senha aleatória forte (usada quando ninguém configurou uma). */
    private String gerarSenhaAleatoria() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BotApiKeyFilter botApiKeyFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Chamado pelo Typebot — validado pelo BotApiKeyFilter (X-Api-Key)
                        .requestMatchers("/api/pedidos/via-bot").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"status\":401,\"error\":\"Não autorizado\","
                                    + "\"message\":\"Credenciais ausentes ou inválidas.\"}");
                }))
                .addFilterBefore(botApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origens = Arrays.stream(origensPermitidas.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Api-Key", "Accept"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        log.info("[SEGURANÇA] CORS liberado para: {}", origens);
        return source;
    }

    /**
     * Impede que o Spring Boot registre o {@link BotApiKeyFilter} também na cadeia
     * padrão do container (ele já roda dentro da cadeia do Spring Security).
     */
    @Bean
    public FilterRegistrationBean<BotApiKeyFilter> botApiKeyFilterRegistration(BotApiKeyFilter filter) {
        FilterRegistrationBean<BotApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
