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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.web.csrf.CsrfException;
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
            .requestMatchers("/api/**").authenticated()
            .anyRequest().denyAll());
        http.formLogin(f -> f.loginProcessingUrl("/api/login").permitAll()
            .successHandler((req,res,auth) -> json(res,200,"Sesión iniciada."))
            .failureHandler((req,res,ex) -> {
                // Solo se informa que la cuenta está desactivada si la contraseña es correcta: así no se revela a terceros.
                if(ex instanceof DisabledException && claveCorrecta(db,req.getParameter("username"),req.getParameter("password")))
                    json(res,401,"Tu cuenta está desactivada. Pide a la coordinación de prácticas que la vuelva a activar.");
                else json(res,401,"El correo o la contraseña no coinciden. Revisa que estén bien escritos e inténtalo de nuevo.");
            }));
        http.logout(l -> l.logoutUrl("/api/logout")
            .logoutSuccessHandler((req,res,auth) -> json(res,200,"Sesión cerrada.")));
        http.exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> json(res,401,"Tu sesión terminó. Inicia sesión nuevamente para continuar."))
            .accessDeniedHandler((req,res,ex) -> json(res,403,ex instanceof CsrfException
                ?"La página estuvo abierta mucho tiempo y tu sesión expiró. Recarga la página (F5) e inténtalo de nuevo."
                :"No tienes permiso para realizar esta acción. Si crees que es un error, contacta a la coordinación.")));
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

    private boolean claveCorrecta(JdbcTemplate db,String correo,String clave) {
        if(correo==null || clave==null)return false;
        return db.queryForList("SELECT clave_hash FROM usuarios WHERE correo=?",String.class,correo.strip().toLowerCase(Locale.ROOT))
            .stream().anyMatch(hash -> passwordEncoder().matches(clave,hash));
    }

    private static void json(HttpServletResponse res,int status,String mensaje) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"mensaje\":\""+mensaje+"\"}");
    }
}
