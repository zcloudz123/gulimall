package com.gulimall.order.interceptor;

import com.gulimall.common.constant.AuthServerConstant;
import com.gulimall.common.vo.MemberRespVo;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @decription:
 * @author: zyy
 * @date 2020-06-23-17:18
 */
public class LoginUserInterceptor extends HandlerInterceptorAdapter {

    public static ThreadLocal<MemberRespVo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        boolean match = new AntPathMatcher().match("/order/order/status/**", request.getRequestURI());
        if(match){
            return true;
        }

        boolean match2 = new AntPathMatcher().match("/payed/notify/**", request.getRequestURI());
        if(match2){
            return true;
        }

        Object attribute = request.getSession().getAttribute(AuthServerConstant.LOGIN_USER);
        if(attribute != null){
            threadLocal.set((MemberRespVo) attribute);
            return true;
        }else{
            //没登录就重定向至登录界面
            request.getSession().setAttribute("msg","请先进行登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
    }
}
