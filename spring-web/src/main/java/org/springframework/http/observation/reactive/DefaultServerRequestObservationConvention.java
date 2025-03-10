/*
 * Copyright 2002-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.http.observation.reactive;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;

/**
 * Default {@link ServerRequestObservationConvention}.
 *
 * @author Brian Clozel
 * @since 6.0
 */
public class DefaultServerRequestObservationConvention implements ServerRequestObservationConvention {

	private static final String DEFAULT_NAME = "http.server.requests";

	private static final KeyValue METHOD_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, "UNKNOWN");

	private static final KeyValue STATUS_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, "UNKNOWN");

	private static final KeyValue HTTP_OUTCOME_SUCCESS = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "SUCCESS");

	private static final KeyValue HTTP_OUTCOME_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, "UNKNOWN");

	private static final KeyValue URI_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "UNKNOWN");

	private static final KeyValue URI_ROOT = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "root");

	private static final KeyValue URI_NOT_FOUND = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "NOT_FOUND");

	private static final KeyValue URI_REDIRECTION = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, "REDIRECTION");

	private static final KeyValue EXCEPTION_NONE = KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, KeyValue.NONE_VALUE);

	private static final KeyValue HTTP_URL_UNKNOWN = KeyValue.of(ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, "UNKNOWN");

	private final String name;

	/**
	 * Create a convention with the default name {@code "http.server.requests"}.
	 */
	public DefaultServerRequestObservationConvention() {
		this(DEFAULT_NAME);
	}

	/**
	 * Create a convention with a custom name.
	 *
	 * @param name the observation name
	 */
	public DefaultServerRequestObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getContextualName(ServerRequestObservationContext context) {
		if (context.getPathPattern() != null) {
			return String.format("http %s %s", context.getCarrier().getMethod().name().toLowerCase(),
					context.getPathPattern().toString());
		}
		return "http " + context.getCarrier().getMethod().name().toLowerCase();
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
		return KeyValues.of(method(context), uri(context), status(context), exception(context), outcome(context));
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
		return KeyValues.of(httpUrl(context));
	}

	protected KeyValue method(ServerRequestObservationContext context) {
		return (context.getCarrier() != null) ? KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.METHOD, context.getCarrier().getMethod().name()) : METHOD_UNKNOWN;
	}

	protected KeyValue status(ServerRequestObservationContext context) {
		if (context.isConnectionAborted()) {
			return STATUS_UNKNOWN;
		}
		return (context.getResponse() != null && context.getResponse().getStatusCode() != null) ?
				KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.STATUS, Integer.toString(context.getResponse().getStatusCode().value())) : STATUS_UNKNOWN;
	}

	protected KeyValue uri(ServerRequestObservationContext context) {
		if (context.getCarrier() != null) {
			PathPattern pattern = context.getPathPattern();
			if (pattern != null) {
				if (pattern.toString().isEmpty()) {
					return URI_ROOT;
				}
				return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.URI, pattern.toString());
			}
			if (context.getResponse() != null && context.getResponse().getStatusCode() != null) {
				HttpStatus status = HttpStatus.resolve(context.getResponse().getStatusCode().value());
				if (status != null) {
					if (status.is3xxRedirection()) {
						return URI_REDIRECTION;
					}
					if (status == HttpStatus.NOT_FOUND) {
						return URI_NOT_FOUND;
					}
				}
			}
		}
		return URI_UNKNOWN;
	}

	protected KeyValue exception(ServerRequestObservationContext context) {
		Throwable error = context.getError();
		if (error != null) {
			String simpleName = error.getClass().getSimpleName();
			return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION,
					StringUtils.hasText(simpleName) ? simpleName : error.getClass().getName());
		}
		return EXCEPTION_NONE;
	}

	protected KeyValue outcome(ServerRequestObservationContext context) {
		if (context.isConnectionAborted()) {
			return HTTP_OUTCOME_UNKNOWN;
		}
		if (context.getResponse() != null && context.getResponse().getStatusCode() != null) {
			return HttpOutcome.forStatus(context.getResponse().getStatusCode());
		}
		return HTTP_OUTCOME_UNKNOWN;
	}

	protected KeyValue httpUrl(ServerRequestObservationContext context) {
		if (context.getCarrier() != null) {
			return KeyValue.of(ServerHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL, context.getCarrier().getPath().toString());
		}
		return HTTP_URL_UNKNOWN;
	}

	static class HttpOutcome {

		static KeyValue forStatus(HttpStatusCode statusCode) {
			if (statusCode.is2xxSuccessful()) {
				return HTTP_OUTCOME_SUCCESS;
			}
			else if (statusCode instanceof HttpStatus status) {
				return KeyValue.of(ServerHttpObservationDocumentation.LowCardinalityKeyNames.OUTCOME, status.series().name());
			}
			else {
				return HTTP_OUTCOME_UNKNOWN;
			}
		}

	}

}
