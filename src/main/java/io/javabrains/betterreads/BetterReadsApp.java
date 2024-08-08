package io.javabrains.betterreads;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.javabrains.betterreads.author.Author;
import io.javabrains.betterreads.author.AuthorRepository;
import io.javabrains.betterreads.book.Book;
import io.javabrains.betterreads.book.BookRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.javabrains.betterreads.connection.DataStaxAstraProperties;

import javax.annotation.PostConstruct;

/**
 * Main application class with main method that runs the Spring Boot app
 */

@SpringBootApplication
@EnableConfigurationProperties(DataStaxAstraProperties.class)
public class BetterReadsApp {

    private AuthorRepository authorRepository;
    private BookRepository bookRepository;

    @Autowired
    public void setAuthorRepository(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Autowired
    public void setBookRepository(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Value("${datadump.location.author}")
    private String authorDumpLocation;

    @Value("${datadump.location.works}")
    private String worksDumpLocation;

    public static void main(String[] args) {
        SpringApplication.run(BetterReadsApp.class, args);
    }

    private void initAuthors() {
        Path path = Paths.get(authorDumpLocation);
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                // Read & parse the line
                String jsonString = line.substring(line.indexOf("{"));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);

                    // Construct Author object
                    Author author = new Author();
                    author.setName(jsonObject.optString("name"));
                    author.setPersonalName(jsonObject.optString("personal_name"));
                    author.setId(jsonObject.optString("key").replace("/authors/", ""));

                    // Persist using Repo
                    authorRepository.save(author);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initWorks() {
        Path path = Paths.get(worksDumpLocation);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                // Read & parse the line
                String jsonString = line.substring(line.indexOf("{"));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);

                    // Construct Book object
                    Book book = new Book();
                    book.setId(jsonObject.getString("key").replace("/works/", ""));
                    book.setName(jsonObject.optString("title"));
                    JSONObject descriptionObj = jsonObject.optJSONObject("description");
                    if (descriptionObj != null) {
                        book.setDescription(descriptionObj.optString("value"));
                    }
                    JSONObject publishedObj = jsonObject.optJSONObject("created");
                    if (publishedObj != null) {
                        String dateStr = publishedObj.getString("value");
                        book.setPublishedDate(LocalDate.parse(dateStr, dateFormat));
                    }
                    JSONArray coversJSONArr = jsonObject.optJSONArray("covers");
                    if (coversJSONArr != null) {
                        List<String> coverIds = new ArrayList<>();
                        for (int i = 0; i < coversJSONArr.length(); i++) {
                            coverIds.add(coversJSONArr.getString(i));
                        }
                        book.setCoverIds(coverIds);
                    }
                    JSONArray authorsJSONArr = jsonObject.optJSONArray("authors");
                    if (authorsJSONArr != null) {
                        List<String> authorIds = new ArrayList<>();
                        for (int i = 0; i < authorsJSONArr.length(); i++) {
                            String authorId = authorsJSONArr.getJSONObject(i).getJSONObject("author").getString("key").replace("/authors/", "");
                            authorIds.add(authorId);
                        }
                        book.setAuthorIds(authorIds);
                        List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id)).map(optionalAuthor -> {
                            if (!optionalAuthor.isPresent()) {
                                return "Unknown Author";
                            }
                            return optionalAuthor.get().getName();
                        }).collect(Collectors.toList());
                        book.setAuthorNames(authorNames);
                    }

                    // Persist using Repo
                    bookRepository.save(book);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void start() {
        System.out.println(authorDumpLocation);
//        initAuthors();
//        initWorks();
        System.out.println("BetterReadsApp saved author");
    }

    /**
     * This is necessary to have the Spring Boot app use the Astra secure bundle
     * to connect to the database
     */
    @Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties astraProperties) {
        Path bundle = astraProperties.getSecureConnectBundle().toPath();
        return builder -> builder.withCloudSecureConnectBundle(bundle);
    }

}
