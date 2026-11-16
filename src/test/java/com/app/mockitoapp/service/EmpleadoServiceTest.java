package com.app.mockitoapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.mockitoapp.model.Cargo;
import com.app.mockitoapp.model.Empleado;
import com.app.mockitoapp.repository.CargoRepository;
import com.app.mockitoapp.repository.EmpleadoRepository;

/**
 * Pruebas unitarias de EmpleadoService con Mockito + JUnit 5.
 * NO se conecta a PostgreSQL: los repositorios JPA se reemplazan por mocks.
 *
 * Estructura de cada test: Arrange (given) / Act (when) / Assert (then).
 *
 * Leyenda de comentarios:
 *   [DOBLE: ...]    tipo de doble de prueba (Dummy, Stub, Mock, Spy, Fake)
 *   [CONCEPTO: ...] concepto clave de Mockito
 *
 * Mockito crea todos los dobles con @Mock; el TIPO depende de cómo se usa
 * en cada test (solo se pasa = Dummy, responde datos = Stub, se verifica = Mock).
 */
@ExtendWith(MockitoExtension.class) // [CONCEPTO: MockitoExtension] Inicializa @Mock, @InjectMocks y @Captor
class EmpleadoServiceTest {

    @Mock // [CONCEPTO: @Mock] crea un doble de la interfaz
    private EmpleadoRepository empleadoRepository;   // simulado

    @Mock // [CONCEPTO: @Mock]
    private CargoRepository cargoRepository;         // simulado

    @InjectMocks // [CONCEPTO: @InjectMocks] crea el servicio REAL e inyecta los mocks por constructor
    private EmpleadoService service;                 // clase real con los mocks inyectados

    @Captor // [CONCEPTO: ArgumentCaptor] declarado con anotación
    private ArgumentCaptor<Empleado> empleadoCaptor;

    private Cargo desarrolladorJunior;

    @BeforeEach
    void setUp() {
        desarrolladorJunior = new Cargo(6, "Desarrollador Junior", "Desarrollo", new BigDecimal("3000.00"));
    }

    private Empleado empleado(Integer id, String nombre, String salario) {
        Empleado e = new Empleado(nombre, "Pérez", nombre.toLowerCase() + "@empresa.com", "999",
                LocalDate.of(2020, 1, 1), new BigDecimal(salario), desarrolladorJunior);
        e.setId(id);
        return e;
    }

    // ---------------------------------------------------------------
    // 1) Stubbing básico: when(...).thenReturn(...)
    // ---------------------------------------------------------------
    /**
     * Qué prueba: buscar un empleado que SÍ existe.
     * Escenario: el repositorio simula que el id 3 es "Carlos Pérez".
     * Resultado esperado: el servicio devuelve ese empleado y consultó el repositorio.
     *test fallido
     */
    @Test
    @DisplayName("buscarPorId devuelve el empleado cuando existe")
    void buscarPorId_existe() {
        // [DOBLE: Stub] empleadoRepository devuelve una respuesta predefinida
        // [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.findById(3)).thenReturn(Optional.of(empleado(3, "Carlos", "13500.00")));

        Empleado resultado = service.buscarPorId(3);

        assertEquals("Carlos Pérez", resultado.getNombreCompleto());
        // [DOBLE: Mock] además se verifica que se hizo la llamada
        // [CONCEPTO: verify(mock).metodo()]
        verify(empleadoRepository).findById(5);
    }

    /**
     * Qué prueba: buscar un empleado que NO existe.
     * Escenario: el repositorio devuelve Optional.empty() para el id 999.
     * Resultado esperado: IllegalArgumentException con el mensaje "Empleado no encontrado: 999".
     */
    @Test
    @DisplayName("buscarPorId lanza excepción cuando no existe")
    void buscarPorId_noExiste() {
        // [DOBLE: Stub] simula que el empleado no existe
        // [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.findById(999)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.buscarPorId(999));

        assertEquals("Empleado no encontrado: 999", ex.getMessage());
    }

    // ---------------------------------------------------------------
    // 2) Stub que devuelve una lista
    // ---------------------------------------------------------------
    /**
     * Qué prueba: la suma de los salarios de todos los empleados.
     * Escenario: el repositorio devuelve 3 empleados (3000.00 + 4500.50 + 2499.50).
     * Resultado esperado: el total es exactamente 10000.00 (con decimales correctos).
     */
    @Test
    @DisplayName("totalNomina suma los salarios de todos los empleados")
    void totalNomina() {
        // [DOBLE: Stub] solo entrega datos; no se verifica nada sobre el mock
        // [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.findAll()).thenReturn(List.of(
                empleado(1, "Ana", "3000.00"),
                empleado(2, "Luis", "4500.50"),
                empleado(3, "Sofía", "2499.50")));

        assertEquals(new BigDecimal("10000.00"), service.totalNomina());
    }

    // ---------------------------------------------------------------
    // 3) thenAnswer + ArgumentCaptor
    // ---------------------------------------------------------------
    /**
     * Qué prueba: una contratación exitosa (camino feliz).
     * Escenario: el email está libre, el cargo 6 existe y la BD asigna el id 100.
     *            El email se envía con espacios y mayúsculas: "  PEDRO.Rojas@Empresa.com ".
     * Resultado esperado: se guarda el empleado con el email normalizado
     *            ("pedro.rojas@empresa.com"), el cargo correcto, fecha de ingreso
     *            y el resultado trae el id 100.
     */
    @Test
    @DisplayName("contratar guarda el empleado con email en minúsculas y cargo asignado")
    void contratar_ok() {
        // [DOBLE: Stub] respuestas predefinidas para pasar las reglas de negocio
        // [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.existsByEmail("pedro.rojas@empresa.com")).thenReturn(false);
        when(cargoRepository.findById(6)).thenReturn(Optional.of(desarrolladorJunior));
        // [DOBLE: Stub] respuesta calculada a partir del argumento recibido
        // [CONCEPTO: thenAnswer] + [CONCEPTO: any() - argument matcher]
        when(empleadoRepository.save(any(Empleado.class))).thenAnswer(inv -> {
            Empleado e = inv.getArgument(0);
            e.setId(100);                // simula el id generado por la BD
            return e;
        });

        Empleado resultado = service.contratar("Pedro", "Rojas", "  PEDRO.Rojas@Empresa.com ",
                "911", 6, new BigDecimal("3200.00"));

        assertEquals(100, resultado.getId());

        // [DOBLE: Mock] se verifica que save() fue llamado
        // [CONCEPTO: verify(mock).metodo()] + [CONCEPTO: ArgumentCaptor]
        verify(empleadoRepository).save(empleadoCaptor.capture());
        Empleado guardado = empleadoCaptor.getValue(); // [CONCEPTO: ArgumentCaptor] revisa el argumento capturado
        assertEquals("pedro.rojas@empresa.com", guardado.getEmail());
        assertEquals("Desarrollador Junior", guardado.getCargo().getNombre());
        assertNotNull(guardado.getFechaIngreso());
    }

    // ---------------------------------------------------------------
    // 4) verify(..., never()) cuando una regla corta el flujo
    // ---------------------------------------------------------------
    /**
     * Qué prueba: la regla "el email no puede repetirse".
     * Escenario: el repositorio indica que el email ya está registrado.
     * Resultado esperado: IllegalStateException, no se guarda nada y ni siquiera
     *            se consulta el cargo (la regla corta el flujo antes).
     */
    @Test
    @DisplayName("contratar falla si el email ya existe y no guarda nada")
    void contratar_emailDuplicado() {
        // [DOBLE: Stub] simula que el email ya está registrado
        // [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.existsByEmail("carlos.mendoza@empresa.com")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.contratar(
                "Carlos", "Mendoza", "carlos.mendoza@empresa.com", null, 3, new BigDecimal("13000")));

        // [DOBLE: Mock] se verifica que NO hubo llamadas
        // [CONCEPTO: never()] + [CONCEPTO: any() / anyInt() - argument matchers]
        verify(empleadoRepository, never()).save(any());
        verify(cargoRepository, never()).findById(anyInt());
    }

    /**
     * Qué prueba: la regla "el salario no puede ser menor al salario base del cargo".
     * Escenario: el cargo exige 3000.00 y se ofrecen 2500.00.
     * Resultado esperado: IllegalArgumentException con el mensaje exacto y no se guarda nada.
     */
    @Test
    @DisplayName("contratar falla si el salario es menor al salario base del cargo")
    void contratar_salarioBajoBase() {
        // [DOBLE: Stub]
        // [CONCEPTO: when(...).thenReturn(...)] + [CONCEPTO: anyString() - argument matcher]
        when(empleadoRepository.existsByEmail(anyString())).thenReturn(false);
        when(cargoRepository.findById(6)).thenReturn(Optional.of(desarrolladorJunior));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.contratar(
                "Ana", "Soto", "ana@empresa.com", null, 6, new BigDecimal("2500.00")));

        assertEquals("El salario no puede ser menor al salario base del cargo (3000.00)", ex.getMessage());
        // [DOBLE: Mock] [CONCEPTO: never()]
        verify(empleadoRepository, never()).save(any());
    }

    /**
     * Qué prueba: la regla "el cargo debe existir".
     * Escenario: el email está libre, pero el cargo 77 no existe.
     * Resultado esperado: IllegalArgumentException, el email se verificó exactamente
     *            1 vez y no se guarda nada.
     */
    @Test
    @DisplayName("contratar falla si el cargo no existe")
    void contratar_cargoNoExiste() {
        // [DOBLE: Stub]
        // [CONCEPTO: when(...).thenReturn(...)] + [CONCEPTO: anyString()]
        when(empleadoRepository.existsByEmail(anyString())).thenReturn(false);
        when(cargoRepository.findById(77)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.contratar(
                "Ana", "Soto", "ana@empresa.com", null, 77, new BigDecimal("5000")));

        // [DOBLE: Mock] [CONCEPTO: times(n)] se esperaba exactamente 1 llamada
        verify(empleadoRepository, times(1)).existsByEmail("ana@empresa.com");
        // [DOBLE: Mock] [CONCEPTO: never()]
        verify(empleadoRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // 5) verifyNoInteractions: validación antes de tocar la BD
    // ---------------------------------------------------------------
    /**
     * Qué prueba: la regla "nombre y apellido obligatorios".
     * Escenario: se envía " " (solo espacios) como nombre.
     * Resultado esperado: IllegalArgumentException y NINGÚN repositorio fue usado:
     *            la validación ocurre antes de ir a la base de datos.
     */
    @Test
    @DisplayName("contratar con nombre vacío no consulta ningún repositorio")
    void contratar_nombreVacio() {
        // [DOBLE: Dummy] los repositorios se inyectan pero nunca se usan:
        // la validación falla antes de llegar a ellos (sin when(...))
        assertThrows(IllegalArgumentException.class, () -> service.contratar(
                " ", "Soto", "x@empresa.com", null, 6, new BigDecimal("5000")));

        // [CONCEPTO: verifyNoInteractions] confirma que los dummies no se tocaron
        verifyNoInteractions(empleadoRepository, cargoRepository);
    }

    // ---------------------------------------------------------------
    // 6) Cálculo + captor en una actualización
    // ---------------------------------------------------------------
    /**
     * Qué prueba: un aumento de salario correcto.
     * Escenario: Lucía (id 5) gana 8200.00 y se aplica un 10%.
     * Resultado esperado: el nuevo salario es 9020.00 y se guardó el empleado con id 5.
     */
    @Test
    @DisplayName("aumentarSalario aplica el porcentaje y guarda el nuevo salario")
    void aumentarSalario_ok() {
        // [DOBLE: Stub] [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.findById(5)).thenReturn(Optional.of(empleado(5, "Lucía", "8200.00")));
        // [DOBLE: Stub] [CONCEPTO: thenAnswer] devuelve el mismo objeto que recibe
        when(empleadoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Empleado resultado = service.aumentarSalario(5, new BigDecimal("10"));

        assertEquals(new BigDecimal("9020.00"), resultado.getSalario());
        // [DOBLE: Mock] [CONCEPTO: verify] + [CONCEPTO: ArgumentCaptor]
        verify(empleadoRepository).save(empleadoCaptor.capture());
        assertEquals(5, empleadoCaptor.getValue().getId());
    }

    /**
     * Qué prueba: la regla "el porcentaje debe ser mayor a 0".
     * Escenario: se intenta un aumento de 0%.
     * Resultado esperado: IllegalArgumentException y no se consultó la base de datos.
     */
    @Test
    @DisplayName("aumentarSalario con porcentaje inválido no consulta la BD")
    void aumentarSalario_porcentajeInvalido() {
        // [DOBLE: Dummy] el repositorio se inyecta pero nunca se usa
        assertThrows(IllegalArgumentException.class,
                () -> service.aumentarSalario(5, BigDecimal.ZERO));

        // [CONCEPTO: verifyNoInteractions]
        verifyNoInteractions(empleadoRepository);
    }

    // ---------------------------------------------------------------
    // 7) thenThrow: simular una falla de la base de datos
    // ---------------------------------------------------------------
    /**
     * Qué prueba: qué pasa si la base de datos falla al guardar.
     * Escenario: todos los datos son válidos, pero save() lanza
     *            "Conexión con PostgreSQL perdida".
     * Resultado esperado: el servicio NO oculta el error; la excepción llega
     *            intacta a quien llamó.
     */
    @Test
    @DisplayName("si la base de datos falla al guardar, la excepción se propaga")
    void contratar_falloBaseDeDatos() {
        // [DOBLE: Stub] [CONCEPTO: when(...).thenReturn(...)]
        when(empleadoRepository.existsByEmail(anyString())).thenReturn(false);
        when(cargoRepository.findById(6)).thenReturn(Optional.of(desarrolladorJunior));
        // [DOBLE: Stub] responde con un error predefinido
        // [CONCEPTO: thenThrow(...)] simula una excepción
        when(empleadoRepository.save(any())).thenThrow(new RuntimeException("Conexión con PostgreSQL perdida"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.contratar(
                "Luis", "Díaz", "luis@empresa.com", null, 6, new BigDecimal("3000")));

        assertEquals("Conexión con PostgreSQL perdida", ex.getMessage());
    }
}
