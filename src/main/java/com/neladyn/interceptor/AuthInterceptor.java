package com.neladyn.interceptor;


import com.neladyn.domain.PathMethod;
import com.neladyn.domain.Role;
import com.neladyn.domain.User;
import com.neladyn.domain.UserDetails;
import com.neladyn.exception.ForbiddenException;
import com.neladyn.service.AuthenticationService;
import com.neladyn.service.RedisService;
import com.neladyn.service.UserService;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthInterceptor.class);

    @Autowired
    private RedisService redisService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserService userService;

    Set<PathMethod> openPaths = new HashSet<PathMethod>(Arrays.asList(
            new PathMethod("/api/login", HttpMethod.GET),
            new PathMethod("/api/logout", HttpMethod.GET),
            new PathMethod("/api/auth", HttpMethod.GET),
            new PathMethod("/api/getLoginUri", HttpMethod.GET)
    ));

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o) throws ForbiddenException {
        String servletPath = httpServletRequest.getServletPath();
        String method = httpServletRequest.getMethod();

        PathMethod pathMethod = new PathMethod(servletPath, HttpMethod.valueOf(method));
        LOGGER.info("Prehandle: path={}, method={}, pathMethod={}", servletPath, method, pathMethod);

        if (!openPaths.contains(pathMethod)) {
            // Needs authentication
            Cookie access_token = WebUtils.getCookie(httpServletRequest, "access_token");
            if (access_token == null || access_token.getValue() == null) {
                LOGGER.warn("Forbidden access_token is null");
                throw new ForbiddenException("Forbidden");
            }

            String access_token_string = access_token.getValue();
            if (!authenticationService.userIsAuthenticatedRedis(access_token_string)) {
                LOGGER.warn("Forbidden {} \t {}", pathMethod, access_token);
                throw new ForbiddenException("Forbidden");
            }

            // Check authorization: If access /api/admin , then role must be ADMIN
            if (pathMethod.getPath().contains("/api/admin")) {
                UserDetails userDetails = authenticationService.getUserDetailsFromAccessToken(access_token_string);
                User user = userService.getUser(userDetails.getEmail());
                LOGGER.info("Check authorization for {}", user);
                if (user.getRole() != Role.ADMIN) {
                    throw new ForbiddenException("Forbidden");
                }
            }

        }


        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object o, Exception e) throws Exception {
    }
}
