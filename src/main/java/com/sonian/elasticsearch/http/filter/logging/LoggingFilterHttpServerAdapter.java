package com.sonian.elasticsearch.http.filter.logging;

import com.sonian.elasticsearch.http.filter.FilterChain;
import com.sonian.elasticsearch.http.filter.FilterHttpServerAdapter;
import org.elasticsearch.common.Classes;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.HttpChannel;
import org.elasticsearch.http.HttpRequest;
import org.elasticsearch.rest.RestResponse;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

/**
 * @author imotov
 */
public class LoggingFilterHttpServerAdapter implements FilterHttpServerAdapter {
    protected volatile ESLogger logger;

    @Inject
    public LoggingFilterHttpServerAdapter(Settings settings, @Assisted String name, @Assisted Settings filterSettings) {
        this.logger = Loggers.getLogger(Classes.getPackageName(getClass()), settings);
    }

    @Override
    public void doFilter(HttpRequest request, HttpChannel channel, FilterChain filterChain) {
        if (logger.isInfoEnabled()) {
            filterChain.doFilter(request, new LoggingHttpChannel(request, channel));
        } else {
            filterChain.doFilter(request, channel);
        }
    }

    private StringBuilder mapToString(Map<String, String> params) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> param : params.entrySet()) {
            if (first) {
                first = false;
            } else {
                buf.append("&");
            }

            buf.append(param.getKey());
            buf.append("=");
            try {
                buf.append(URLEncoder.encode(param.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                logger.error("UnsupportedEncodingException", ex);
            }
        }
        if (first) {
            buf.append("-");
        }
        return buf;
    }

    private class LoggingHttpChannel implements HttpChannel {
        private final HttpChannel channel;

        private final String method;

        private final String path;

        private final StringBuilder params;

        private final long timestamp;

        private final String content;

        public LoggingHttpChannel(HttpRequest request, HttpChannel channel) {
            this.channel = channel;
            method = request.method().name();
            path = request.rawPath();
            params = mapToString(request.params());
            timestamp = System.currentTimeMillis();
            content = request.contentAsString();
        }


        @Override
        public void sendResponse(RestResponse response) {
            int contentLength = -1;
            try {
                contentLength = response.contentLength();
            } catch (IOException ex) {
                // Ignore
            }
            channel.sendResponse(response);
            long latency = System.currentTimeMillis() - timestamp;
            logger.info("{} {} {} {} {} {} {} [{}]",
                    method,
                    path,
                    params,
                    response.status().getStatus(),
                    response.status(),
                    contentLength >= 0 ? contentLength : "-",
                    latency,
                    content);
        }
    }

}