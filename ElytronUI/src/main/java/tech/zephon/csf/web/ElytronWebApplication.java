package tech.zephon.csf.web;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ElytronWebApplication extends SpringBootServletInitializer {
    public static void main(String[] args) {
        SpringApplication.run(ElytronWebApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ElytronWebApplication.class);
    }

    @Bean
    Filter responseHeaders() {
        return (request, response, chain) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setHeader("Cache-Control", "no-store");
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("Content-Security-Policy", "default-src 'self'; object-src 'none'; "
                    + "base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
            chain.doFilter(request, response);
        };
    }
}
