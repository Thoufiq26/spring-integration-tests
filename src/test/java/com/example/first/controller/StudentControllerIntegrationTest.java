package com.tutorial.first.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tutorial.first.model.Student;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class StudentControllerIntegrationTest {

    private final String baseUrl = "http://localhost:8082/students";
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentControllerIntegrationTest() {
        // Initialize MongoTemplate with QA database
        org.springframework.data.mongodb.core.MongoClientFactoryBean mongo = new org.springframework.data.mongodb.core.MongoClientFactoryBean();
        mongo.setHost("localhost");
        mongo.setPort(27017);
        try {
            this.mongoTemplate = new MongoTemplate(mongo.getObject(), "qa_db");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MongoTemplate", e);
        }
    }

    @BeforeMethod
    public void setUp() {
        // Clear qa_db before each test
        mongoTemplate.dropCollection("students");
    }

    @Test
    public void testHealthCheck() {
        given()
            .when()
            .get(baseUrl + "/health")
            .then()
            .statusCode(200)
            .body("status", org.hamcrest.Matchers.equalTo("UP"))
            .body("stage", org.hamcrest.Matchers.equalTo("qa"));
    }

    @Test
    public void testGetAllStudents_EmptyList() {
        List<Student> students = given()
            .when()
            .get(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student[].class);

        assertEquals(students.length, 0);
    }

    @Test
    public void testCreateAndGetAllStudents() throws Exception {
        Student student = new Student(null, "John Doe", "john@example.com");
        String requestBody = objectMapper.writeValueAsString(student);

        // Create student
        Student created = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class);

        assertNotNull(created.getId());
        assertEquals(created.getName(), "John Doe");
        assertEquals(created.getEmail(), "john@example.com");

        // Get all students
        List<Student> students = given()
            .when()
            .get(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student[].class);

        assertEquals(students.length, 1);
        assertEquals(students[0].getName(), "John Doe");
    }

    @Test
    public void testGetStudentById() throws Exception {
        Student student = new Student(null, "Jane Smith", "jane@example.com");
        String requestBody = objectMapper.writeValueAsString(student);

        // Create student
        String id = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class)
            .getId();

        // Get by ID
        Student retrieved = given()
            .when()
            .get(baseUrl + "/" + id)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class);

        assertEquals(retrieved.getId(), id);
        assertEquals(retrieved.getName(), "Jane Smith");
        assertEquals(retrieved.getEmail(), "jane@example.com");
    }

    @Test
    public void testGetStudentById_NotFound() {
        given()
            .when()
            .get(baseUrl + "/nonexistent")
            .then()
            .statusCode(200)
            .body(org.hamcrest.Matchers.isEmptyOrNullString());
    }

    @Test
    public void testUpdateStudent() throws Exception {
        // Create student
        Student student = new Student(null, "Alice Brown", "alice@example.com");
        String requestBody = objectMapper.writeValueAsString(student);

        String id = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class)
            .getId();

        // Update student
        Student updatedStudent = new Student(null, "Alice Updated", "alice.updated@example.com");
        String updateRequestBody = objectMapper.writeValueAsString(updatedStudent);

        Student updated = given()
            .contentType(ContentType.JSON)
            .body(updateRequestBody)
            .when()
            .put(baseUrl + "/" + id)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class);

        assertEquals(updated.getId(), id);
        assertEquals(updated.getName(), "Alice Updated");
        assertEquals(updated.getEmail(), "alice.updated@example.com");

        // Verify in database
        Student fromDb = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(id)), Student.class);
        assertNotNull(fromDb);
        assertEquals(fromDb.getName(), "Alice Updated");
    }

    @Test
    public void testDeleteStudent() throws Exception {
        // Create student
        Student student = new Student(null, "Bob Wilson", "bob@example.com");
        String requestBody = objectMapper.writeValueAsString(student);

        String id = given()
            .contentType(ContentType.JSON)
            .body(requestBody)
            .when()
            .post(baseUrl)
            .then()
            .statusCode(200)
            .extract()
            .body()
            .as(Student.class)
            .getId();

        // Delete student
        given()
            .when()
            .delete(baseUrl + "/" + id)
            .then()
            .statusCode(200);

        // Verify deletion
        Student fromDb = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(id)), Student.class);
        assertTrue(fromDb == null);
    }
}