package com.smartpark.api.vehicle.application.service;

import com.smartpark.api.shared.dto.PageResponse;
import com.smartpark.api.shared.exception.ConflictException;
import com.smartpark.api.shared.exception.ErrorCode;
import com.smartpark.api.shared.exception.NotFoundException;
import com.smartpark.api.vehicle.api.dto.RegisterVehicleRequest;
import com.smartpark.api.vehicle.api.dto.VehicleResponse;
import com.smartpark.api.vehicle.domain.model.Vehicle;
import com.smartpark.api.vehicle.domain.model.VehicleType;
import com.smartpark.api.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("VehicleServiceImpl Tests")
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    // Matches pattern ^[A-Za-z0-9\-]+$ and within size constraints (min=8, max=20)
    private static final String LICENSE_PLATE = "ABC-12345";
    // Matches pattern ^[A-Za-z ]+$ and within size constraints (min=3, max=100)
    private static final String OWNER_NAME    = "Juan dela Cruz";
    private static final VehicleType TYPE     = VehicleType.CAR;

    private RegisterVehicleRequest buildRequest() {
        return new RegisterVehicleRequest(LICENSE_PLATE, TYPE, OWNER_NAME);
    }

    private Vehicle buildVehicle() {
        return Vehicle.builder()
                .licensePlate(LICENSE_PLATE)
                .vehicleType(TYPE)
                .ownerName(OWNER_NAME)
                .build();
    }

    @Nested
    @DisplayName("registerVehicle()")
    class RegisterVehicle {

        @Test
        @DisplayName("Should register and return a VehicleResponse when license plate is unique")
        void shouldRegisterVehicle_whenLicensePlateIsUnique() {
            // Arrange
            RegisterVehicleRequest request = buildRequest();
            Vehicle saved = buildVehicle();

            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(false);
            given(vehicleRepository.save(any(Vehicle.class))).willReturn(saved);

            // Act
            VehicleResponse response = vehicleService.registerVehicle(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.licensePlate()).isEqualTo(LICENSE_PLATE);
            assertThat(response.vehicleType()).isEqualTo(TYPE);
            assertThat(response.ownerName()).isEqualTo(OWNER_NAME);

            then(vehicleRepository).should().existsByLicensePlate(LICENSE_PLATE);
            then(vehicleRepository).should().save(any(Vehicle.class));
        }

        @Test
        @DisplayName("Should throw ConflictException when license plate already exists")
        void shouldThrowConflictException_whenLicensePlateAlreadyExists() {
            // Arrange
            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> vehicleService.registerVehicle(buildRequest()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(LICENSE_PLATE);

            // save() must never be reached when the plate already exists
            then(vehicleRepository).should().existsByLicensePlate(LICENSE_PLATE);
            then(vehicleRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Should set VEHICLE_ALREADY_EXISTS error code on ConflictException")
        void shouldSetVehicleAlreadyExistsErrorCode_onConflictException() {
            // Arrange
            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(true);

            // Act
            // ConflictException(String, ErrorCode) > BusinessException > @Getter getErrorCode()
            ConflictException ex = catchThrowableOfType(
                    () -> vehicleService.registerVehicle(buildRequest()),
                    ConflictException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_ALREADY_EXISTS);
            assertThat(ex.getErrorCode().getValue()).isEqualTo("VEHICLE_ALREADY_EXISTS");
        }

        @Test
        @DisplayName("ConflictException should be an unchecked (RuntimeException) exception")
        void conflictException_shouldBeRuntimeException() {
            // Arrange
            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(true);

            // Act
            ConflictException ex = catchThrowableOfType(
                    () -> vehicleService.registerVehicle(buildRequest()),
                    ConflictException.class
            );

            // Assert: ConflictException > BusinessException > RuntimeException
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should build the Vehicle entity from all request fields before saving")
        void shouldBuildVehicleWithAllFieldsFromRequest() {
            // Arrange
            RegisterVehicleRequest request = buildRequest();
            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(false);

            // Return the argument passed to save() so we can inspect the built entity via the response
            given(vehicleRepository.save(any(Vehicle.class))).willAnswer(inv -> inv.getArgument(0));

            // Act
            VehicleResponse response = vehicleService.registerVehicle(request);

            // Assert: all three record fields on the request must flow into the entity
            assertThat(response.licensePlate()).isEqualTo(request.licensePlate());
            assertThat(response.vehicleType()).isEqualTo(request.vehicleType());
            assertThat(response.ownerName()).isEqualTo(request.ownerName());
        }

        @Test
        @DisplayName("Should map the persisted Vehicle returned by the repository to the response")
        void shouldMapPersistedVehicleToResponse_notRawRequest() {
            // Arrange: simulate a persistence layer that returns a modified entity
            RegisterVehicleRequest request = buildRequest();
            Vehicle persisted = Vehicle.builder()
                    .licensePlate(LICENSE_PLATE)
                    .vehicleType(VehicleType.TRUCK)         // differs from request (CAR)
                    .ownerName("System Persisted Name")     // differs from request
                    .build();

            given(vehicleRepository.existsByLicensePlate(LICENSE_PLATE)).willReturn(false);
            given(vehicleRepository.save(any(Vehicle.class))).willReturn(persisted);

            // Act
            VehicleResponse response = vehicleService.registerVehicle(request);

            // Assert: toResponse() must use save()'s return value, not the original request
            assertThat(response.vehicleType()).isEqualTo(VehicleType.TRUCK);
            assertThat(response.ownerName()).isEqualTo("System Persisted Name");
        }
    }

    @Nested
    @DisplayName("getVehicle()")
    class GetVehicle {

        @Test
        @DisplayName("Should return a VehicleResponse when the license plate exists")
        void shouldReturnVehicleResponse_whenFound() {
            // Arrange
            Vehicle vehicle = buildVehicle();
            given(vehicleRepository.findByLicensePlate(LICENSE_PLATE)).willReturn(Optional.of(vehicle));

            // Act
            VehicleResponse response = vehicleService.getVehicle(LICENSE_PLATE);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.licensePlate()).isEqualTo(LICENSE_PLATE);
            assertThat(response.vehicleType()).isEqualTo(TYPE);
            assertThat(response.ownerName()).isEqualTo(OWNER_NAME);

            then(vehicleRepository).should().findByLicensePlate(LICENSE_PLATE);
        }

        @Test
        @DisplayName("Should throw NotFoundException when license plate does not exist")
        void shouldThrowNotFoundException_whenNotFound() {
            // Arrange
            given(vehicleRepository.findByLicensePlate(LICENSE_PLATE)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> vehicleService.getVehicle(LICENSE_PLATE))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(LICENSE_PLATE);

            then(vehicleRepository).should().findByLicensePlate(LICENSE_PLATE);
        }

        @Test
        @DisplayName("Should set VEHICLE_NOT_FOUND error code on NotFoundException")
        void shouldSetVehicleNotFoundErrorCode_onNotFoundException() {
            // Arrange
            given(vehicleRepository.findByLicensePlate(LICENSE_PLATE)).willReturn(Optional.empty());

            // Act
            // Service calls: new NotFoundException(msg, ErrorCode.VEHICLE_NOT_FOUND)
            // which routes to NotFoundException(String, ErrorCode) > super(message, errorCode)
            // NOT the default single-arg constructor (which would give RESOURCE_NOT_FOUND)
            NotFoundException ex = catchThrowableOfType(
                    () -> vehicleService.getVehicle(LICENSE_PLATE),
                    NotFoundException.class
            );

            // Assert
            assertThat(ex).isNotNull();
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND);
            assertThat(ex.getErrorCode().getValue()).isEqualTo("VEHICLE_NOT_FOUND");
        }

        @Test
        @DisplayName("NotFoundException should be an unchecked (RuntimeException) exception")
        void notFoundException_shouldBeRuntimeException() {
            // Arrange
            given(vehicleRepository.findByLicensePlate(LICENSE_PLATE)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> vehicleService.getVehicle(LICENSE_PLATE),
                    NotFoundException.class
            );

            // Assert: NotFoundException > BusinessException > RuntimeException
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Should NOT use the default RESOURCE_NOT_FOUND code — service overrides with VEHICLE_NOT_FOUND")
        void shouldNotUseDefaultResourceNotFoundCode() {
            // Arrange
            given(vehicleRepository.findByLicensePlate(LICENSE_PLATE)).willReturn(Optional.empty());

            // Act
            NotFoundException ex = catchThrowableOfType(
                    () -> vehicleService.getVehicle(LICENSE_PLATE),
                    NotFoundException.class
            );

            // Assert: the single-arg constructor defaults to RESOURCE_NOT_FOUND, but the service explicitly passes
            // VEHICLE_NOT_FOUND
            assertThat(ex.getErrorCode()).isNotEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VEHICLE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getAllVehicles()")
    class GetAllVehicles {

        @Test
        @DisplayName("Should return a PageResponse with correctly mapped VehicleResponses")
        void shouldReturnPageResponse_withMappedContent() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Vehicle v1 = buildVehicle();
            Vehicle v2 = Vehicle.builder()
                    .licensePlate("XYZ-56789") // valid: letters/digits/dashes, 9 chars
                    .vehicleType(VehicleType.MOTORCYCLE)
                    .ownerName("Maria Santos")
                    .build();

            Page<Vehicle> vehiclePage = new PageImpl<>(List.of(v1, v2), pageable, 2);
            given(vehicleRepository.findAll(pageable)).willReturn(vehiclePage);

            // Act
            PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

            // Assert: content
            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(2);
            assertThat(result.content())
                    .extracting(VehicleResponse::licensePlate)
                    .containsExactly(LICENSE_PLATE, "XYZ-56789");

            // Assert: PageResponse record fields
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(2L);
            assertThat(result.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return an empty PageResponse when no vehicles exist")
        void shouldReturnEmptyPageResponse_whenNoVehicles() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Vehicle> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(vehicleRepository.findAll(pageable)).willReturn(emptyPage);

            // Act
            PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
            assertThat(result.totalPages()).isZero();
        }

        @Test
        @DisplayName("Should correctly compute pagination metadata for a non-first page")
        void shouldComputePaginationMetadata_forNonFirstPage() {
            // Arrange:  page 1, size 5, 11 total elements > ceil(11/5) = 3 total pages
            Pageable pageable = PageRequest.of(1, 5);
            Page<Vehicle> page = new PageImpl<>(List.of(buildVehicle()), pageable, 11);
            given(vehicleRepository.findAll(pageable)).willReturn(page);

            // Act
            PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

            // Assert
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(5);
            assertThat(result.totalElements()).isEqualTo(11L);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("Should fully map all three VehicleResponse fields for each item in the page")
        void shouldMapAllVehicleFields_forEachItemInPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Vehicle> page = new PageImpl<>(List.of(buildVehicle()), pageable, 1);
            given(vehicleRepository.findAll(pageable)).willReturn(page);

            // Act
            PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

            // Assert: VehicleResponse is a @Builder record with licensePlate, vehicleType, ownerName
            VehicleResponse item = result.content().get(0);
            assertThat(item.licensePlate()).isEqualTo(LICENSE_PLATE);
            assertThat(item.vehicleType()).isEqualTo(TYPE);
            assertThat(item.ownerName()).isEqualTo(OWNER_NAME);
        }

        @Test
        @DisplayName("Should call repository findAll exactly once with the given Pageable")
        void shouldCallFindAllExactlyOnce_withGivenPageable() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 20, Sort.by("licensePlate"));
            Page<Vehicle> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(vehicleRepository.findAll(pageable)).willReturn(emptyPage);

            // Act
            vehicleService.getAllVehicles(pageable);

            // Assert
            then(vehicleRepository).should(times(1)).findAll(pageable);
            then(vehicleRepository).shouldHaveNoMoreInteractions();
        }

        @Test
        @DisplayName("Should support all three VehicleType enum values in a page")
        void shouldSupportAllVehicleTypes_inPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            List<Vehicle> vehicles = List.of(
                    Vehicle.builder().licensePlate("AAA-11111").vehicleType(VehicleType.CAR).ownerName("Alice Tan").build(),
                    Vehicle.builder().licensePlate("BBB-22222").vehicleType(VehicleType.MOTORCYCLE).ownerName("Bob Cruz").build(),
                    Vehicle.builder().licensePlate("CCC-33333").vehicleType(VehicleType.TRUCK).ownerName("Carlo Reyes").build()
            );
            Page<Vehicle> page = new PageImpl<>(vehicles, pageable, 3);
            given(vehicleRepository.findAll(pageable)).willReturn(page);

            // Act
            PageResponse<VehicleResponse> result = vehicleService.getAllVehicles(pageable);

            // Assert: all three enum constants are preserved through the mapping
            assertThat(result.content())
                    .extracting(VehicleResponse::vehicleType)
                    .containsExactly(VehicleType.CAR, VehicleType.MOTORCYCLE, VehicleType.TRUCK);
        }
    }
}