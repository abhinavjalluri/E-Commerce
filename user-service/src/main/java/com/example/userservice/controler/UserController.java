package com.example.userservice.controler;

import com.example.userservice.config.context.ContextHolder;
import com.example.userservice.config.security.RequiresAuthentication;
import com.example.userservice.dto.*;
import com.example.userservice.service.IUserService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping(path = {"api/v1/users"})
@Tag(name = "User")
public class UserController {

  private final IUserService userService;
  private final ContextHolder contextHolder;

  @Operation(
    summary = "Create a new user",
    responses = {
      @ApiResponse(responseCode = "201", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class))),
      @ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    }
  )
  @PostMapping
  public ResponseEntity<UserDto> createUser(HttpServletRequest request, @Valid @RequestBody UserCreateDto userCreateDto) {
    logRequest(request, userCreateDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.addOne(userCreateDto));
  }

  @Hidden
  @PostMapping("/login")
  public ResponseEntity<UserDto> getUserLogin(HttpServletRequest request, @RequestBody LoginDto loginDto) {
    logRequest(request, null);
    return ResponseEntity.ok(userService.getUserLogin(loginDto));
  }

  @Operation(
    summary = "Get the current user",
    responses = {
      @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class))),
      @ApiResponse(responseCode = "400", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @GetMapping("/me")
  @RequiresAuthentication
  public ResponseEntity<UserDto> getUser(HttpServletRequest request) {
    logRequest(request, null);
    return ResponseEntity.ok(userService.getUser());
  }

  @Operation(
    summary = "Get all users (paginated)",
    description = "Page is zero-based. Size is capped at 100. sortBy: id, name, username, email, createdAt, updatedAt.",
    responses = {
      @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PageResponse.class)))
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @GetMapping
  public ResponseEntity<PageResponse<UserDto>> getAllUsers(
    HttpServletRequest request,
    @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
    @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "10") int size,
    @Parameter(description = "Sort field") @RequestParam(defaultValue = "id") String sortBy,
    @Parameter(description = "asc or desc") @RequestParam(defaultValue = "asc") String direction
  ) {
    logRequest(request, null);
    return ResponseEntity.ok(userService.getAllUsers(page, size, sortBy, direction));
  }

  @Operation(
    summary = "Get a user by id",
    responses = {
      @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class))),
      @ApiResponse(responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @GetMapping("/{id}")
  @RequiresAuthentication
  public ResponseEntity<UserDto> getUserById(HttpServletRequest request, @PathVariable Long id) {
    logRequest(request, null);
    return ResponseEntity.ok(userService.getUserById(id));
  }

  @Operation(
    summary = "Update a user",
    description = "The user is identified by the id inside the request body. Redis cache is updated after commit.",
    responses = {
      @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = UserDto.class))),
      @ApiResponse(responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @PutMapping
  @RequiresAuthentication
  public ResponseEntity<UserDto> editUser(HttpServletRequest request, @Valid @RequestBody UserEditDto userEditDto) {
    logRequest(request, userEditDto);
    return ResponseEntity.ok(userService.editUser(userEditDto));
  }

  @Operation(
    summary = "Delete a user",
    description = "Deletes the user and evicts it from the Redis cache.",
    responses = {
      @ApiResponse(responseCode = "204", description = "Deleted"),
      @ApiResponse(responseCode = "404", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @DeleteMapping("/{id}")
  @RequiresAuthentication
  public ResponseEntity<Void> deleteUser(HttpServletRequest request, @PathVariable Long id) {
    logRequest(request, id);
    userService.deleteUser(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(
    summary = "Refresh the Redis user cache from the database",
    description = "Reads all users from the DB and rewrites the cache, removing entries for deleted users.",
    responses = {
      @ApiResponse(responseCode = "200", description = "Number of users cached")
    },
    security = @SecurityRequirement(name = "bearerAuth")
  )
  @PostMapping("/cache/refresh")
  public ResponseEntity<Map<String, Integer>> refreshCache(HttpServletRequest request) {
    logRequest(request, null);
    return ResponseEntity.ok(Map.of("cachedUsers", userService.refreshCache()));
  }

  private void logRequest(HttpServletRequest request, Object obj) {
    log.info("{} - {} - {} - {} - {}", request.getMethod(), request.getRequestURI(),
      contextHolder.getCorrelationId(), contextHolder.getUsername(), obj);
  }
}