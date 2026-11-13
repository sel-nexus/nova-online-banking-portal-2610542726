package com.nova.bank.config;
import com.nova.bank.auth.*;
import com.nova.bank.common.ErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
/** Enforce stateless authorization and fail closed for every invalid bearer or database failure. */
@Configuration @EnableMethodSecurity
public class SecurityConfig {
 /** Write the same safe error shape used by controller advice. */
 private static void error(HttpServletResponse response,ObjectMapper mapper,int status,String message) throws IOException {response.setStatus(status);response.setContentType("application/json");mapper.writeValue(response.getOutputStream(),ErrorHandler.body(status,message));}
 /** Register bearer verification ahead of username/password authentication. */
 @Bean SecurityFilterChain security(HttpSecurity http,TokenService tokens,ObjectMapper mapper) throws Exception {
  OncePerRequestFilter filter=new OncePerRequestFilter(){
   /** Keep explicit public endpoints independent of stale bearer credentials. */
   @Override protected boolean shouldNotFilter(HttpServletRequest r){return (r.getMethod().equals("POST")&&r.getServletPath().equals("/api/v1/auth/login")) || (r.getMethod().equals("GET")&&r.getServletPath().equals("/api/health")) || r.getMethod().equals("OPTIONS");}
   /** Reject malformed or unverifiable bearers before invoking application code. */
   @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain) throws ServletException,IOException {
    String h=r.getHeader("Authorization");
    if(h!=null){
     try{if(!h.startsWith("Bearer ")||h.length()<8)throw new IllegalArgumentException("Invalid bearer");User u=tokens.verify(h.substring(7));SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u.getId(),null,List.of(new SimpleGrantedAuthority(u.getRole()))));}
     catch(Exception e){SecurityContextHolder.clearContext();error(s,mapper,401,"Authentication required.");return;}
    }
    chain.doFilter(r,s);
   }
  };
  return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/health").permitAll().requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/auth/login").permitAll().requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->error(s,mapper,401,"Authentication required.")).accessDeniedHandler((r,s,x)->error(s,mapper,403,"Access denied.")))
   .addFilterBefore(filter,UsernamePasswordAuthenticationFilter.class).build();
 }
 /** Permit only configured browser origins and bearer headers. */
 @Bean CorsConfigurationSource cors(@Value("${nova.cors-origins}") String origins){CorsConfiguration c=new CorsConfiguration();c.setAllowedOrigins(Arrays.stream(origins.split(",")).filter(s->!s.isBlank()).toList());c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));c.setAllowedHeaders(List.of("Authorization","Content-Type"));c.setExposedHeaders(List.of("Content-Disposition"));UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",c);return source;}
}
