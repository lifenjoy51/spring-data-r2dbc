/*
 * Copyright 2018-2020 the original author or authors.
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
package org.springframework.data.r2dbc.repository;

import static org.assertj.core.api.Assertions.*;

import io.r2dbc.postgresql.codec.Json;
import io.r2dbc.spi.ConnectionFactory;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.sql.DataSource;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.annotation.Id;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.springframework.data.r2dbc.testing.ExternalDatabase;
import org.springframework.data.r2dbc.testing.PostgresTestSupport;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Integration tests for {@link LegoSetRepository} using {@link R2dbcRepositoryFactory} against Postgres.
 *
 * @author Mark Paluch
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class PostgresR2dbcRepositoryIntegrationTests extends AbstractR2dbcRepositoryIntegrationTests {

	@ClassRule public static final ExternalDatabase database = PostgresTestSupport.database();

	@Autowired JsonPersonRepository jsonPersonRepository;

	@Configuration
	@EnableR2dbcRepositories(considerNestedRepositories = true,
			includeFilters = @Filter(classes = { PostgresLegoSetRepository.class, JsonPersonRepository.class },
					type = FilterType.ASSIGNABLE_TYPE))
	static class IntegrationTestConfiguration extends AbstractR2dbcConfiguration {

		@Bean
		@Override
		public ConnectionFactory connectionFactory() {
			return PostgresTestSupport.createConnectionFactory(database);
		}
	}

	@Override
	protected DataSource createDataSource() {
		return PostgresTestSupport.createDataSource(database);
	}

	@Override
	protected ConnectionFactory createConnectionFactory() {
		return PostgresTestSupport.createConnectionFactory(database);
	}

	@Override
	protected String getCreateTableStatement() {
		return PostgresTestSupport.CREATE_TABLE_LEGOSET_WITH_ID_GENERATION;
	}

	@Override
	protected Class<? extends LegoSetRepository> getRepositoryInterfaceType() {
		return PostgresLegoSetRepository.class;
	}

	interface PostgresLegoSetRepository extends LegoSetRepository {

		@Override
		@Query("SELECT name FROM legoset")
		Flux<Named> findAsProjection();

		@Override
		@Query("SELECT * FROM legoset WHERE manual = :manual")
		Mono<LegoSet> findByManual(int manual);

		@Override
		@Query("SELECT id FROM legoset")
		Flux<Integer> findAllIds();
	}

	@Test
	public void shouldSaveAndLoadJson() {

		JdbcTemplate template = new JdbcTemplate(createDataSource());

		template.execute("DROP TABLE IF EXISTS json_person");
		template.execute("CREATE TABLE json_person (\n" //
				+ "    id          SERIAL PRIMARY KEY,\n" //
				+ "    json_value  JSONB NOT NULL" //
				+ ");");

		JsonPerson person = new JsonPerson(null, Json.of("{\"hello\": \"world\"}"));
		jsonPersonRepository.save(person).as(StepVerifier::create).expectNextCount(1).verifyComplete();

		jsonPersonRepository.findAll().as(StepVerifier::create).consumeNextWith(actual -> {

			assertThat(actual.jsonValue).isNotNull();
			assertThat(actual.jsonValue.asString()).isEqualTo("{\"hello\": \"world\"}");
		}).verifyComplete();
	}

	@AllArgsConstructor
	static class JsonPerson {

		@Id Long id;

		Json jsonValue;
	}

	interface JsonPersonRepository extends ReactiveCrudRepository<JsonPerson, Long> {

	}
}
