package cl.ubb.practicas;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;

@Configuration
public class SeguridadConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService usuariosLogin(JdbcTemplate db) {
        return correo -> db.query("SELECT correo,clave_hash,rol,activo FROM usuarios WHERE correo=?",
            (rs,n) -> User.withUsername(rs.getString("correo")).password(rs.getString("clave_hash"))
                .roles(rs.getString("rol")).disabled(!rs.getBoolean("activo")).build(),
            correo.strip().toLowerCase(Locale.ROOT)).stream().findFirst()
            .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas."));
    }

    @Bean SecurityFilterChain seguridad(HttpSecurity http, JdbcTemplate db) throws Exception {
        http.authorizeHttpRequests(a -> a
            .requestMatchers("/", "/index.html", "/styles.css", "/app.js", "/modules/**", "/favicon.ico", "/api/sesion", "/error").permitAll()
            .requestMatchers("/api/cuenta/**").authenticated()
            .requestMatchers("/api/**").hasRole("COORDINADOR")
            .anyRequest().denyAll());
        http.formLogin(f -> f.loginProcessingUrl("/api/login").permitAll()
            .successHandler((req,res,auth) -> json(res,200,"Sesión iniciada."))
            .failureHandler((req,res,ex) -> json(res,401,"Correo o contraseña incorrectos, o cuenta desactivada.")));
        http.logout(l -> l.logoutUrl("/api/logout")
            .logoutSuccessHandler((req,res,auth) -> json(res,200,"Sesión cerrada.")));
        http.exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> json(res,401,"Inicia sesión para continuar."))
            .accessDeniedHandler((req,res,ex) -> json(res,403,"No tienes permiso o tu sesión ha vencido. Actualiza la página.")));
        http.requestCache(c -> c.disable());
        // Verifica la activación en cada petición, no solamente al iniciar sesión.
        http.addFilterAfter(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if(auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                    Integer activos=db.queryForObject("SELECT COUNT(*) FROM usuarios WHERE correo=? AND activo=TRUE",Integer.class,auth.getName());
                    if(activos==null || activos==0) {
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                        if(req.getSession(false)!=null)req.getSession(false).invalidate();
                        json(res,401,"La cuenta está desactivada. Inicia sesión nuevamente.");return;
                    }
                }
                chain.doFilter(req,res);
            }
        }, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res,int status,String mensaje) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"mensaje\":\""+mensaje+"\"}");
    }
}
