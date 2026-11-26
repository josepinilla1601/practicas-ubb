package cl.ubb.practicas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Sprint 3: bitácora, ficha de inscripción, rúbrica MBE, carta de presentación, importación, calendario y vista previa. */
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username="coordinacion@test.example",roles="COORDINADOR")
class Sprint3IntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    MockMvc mvc;
    long practica,practicaSinColegio,estudiante,colegio,profe;
    RequestPostProcessor alumno,colegioUser,profeUser,otroAlumno;

    @BeforeEach void preparar() throws Exception {
        assertTrue(db.queryForObject("SELECT DATABASE()",String.class).endsWith("_test"));
        for(String t:new String[]{"rubricas_colegio","fichas_inscripcion","bitacora_jornadas","calificaciones","asistente_mensajes","material_fragmentos","materiales","evaluaciones_colegio","revisiones","documentos","requisitos_practica","practicas","usuarios","estudiantes","centros"})db.update("DELETE FROM "+t);
        db.update("INSERT INTO usuarios(correo,nombre,clave_hash,rol) VALUES('coordinacion@test.example','Coordinación','no-utilizada','COORDINADOR')");
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).defaultRequest(get("/").with(csrf())).build();
        long centro=crear("/centros",Map.of("nombre","Liceo de prueba","comuna","Chillán","direccion","Calle 123","contacto","Directora"),201).get("id").asLong();
        estudiante=crear("/estudiantes",Map.of("identificador","S3-1","nombres","Ana","apellidos","Rojas Pérez","correo","ana@test.example"),201).get("id").asLong();
        long otro=crear("/estudiantes",Map.of("identificador","S3-2","nombres","Luis","apellidos","Soto","correo","luis@test.example"),201).get("id").asLong();
        practica=crear("/practicas",datos(estudiante,"PED",centro),201).get("id").asLong();
        practicaSinColegio=crear("/practicas",datos(otro,"INI1",null),201).get("id").asLong();
        cuenta("s3-alumno@test.example","ESTUDIANTE",estudiante);cuenta("s3-otro@test.example","ESTUDIANTE",otro);
        colegio=cuenta("s3-colegio@test.example","PROFESOR_COLEGIO",null);profe=cuenta("s3-profe@test.example","PROFESOR_UBB",null);
        mvc.perform(put("/api/practicas/"+practica+"/docentes").contentType("application/json").content("{\"profesorUbbId\":"+profe+",\"profesorColegioId\":"+colegio+"}")).andExpect(status().isOk());
        alumno=user("s3-alumno@test.example").roles("ESTUDIANTE");otroAlumno=user("s3-otro@test.example").roles("ESTUDIANTE");
        colegioUser=user("s3-colegio@test.example").roles("PROFESOR_COLEGIO");profeUser=user("s3-profe@test.example").roles("PROFESOR_UBB");
    }
    Map<String,Object> datos(long est,String asig,Long centro){var m=new HashMap<String,Object>();m.put("estudianteId",est);m.put("asignaturaId",db.queryForObject("SELECT id FROM asignaturas WHERE codigo=?",Long.class,asig));m.put("anio",2026);m.put("semestre",2);m.put("profesorResponsable","Profesor de prueba");m.put("centroId",centro);return m;}
    long cuenta(String correo,String rol,Long est)throws Exception{var d=new HashMap<String,Object>();d.put("correo",correo);d.put("nombre","Persona "+rol);d.put("clave","ClavePrueba2026");d.put("rol",rol);d.put("estudianteId",est);return crear("/usuarios",d,201).get("id").asLong();}
    JsonNode crear(String ruta,Object body,int status)throws Exception{return enviar(post("/api"+ruta),body,status,null);}
    JsonNode enviar(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,Object body,int status,RequestPostProcessor quien)throws Exception{
        if(quien!=null)req=req.with(quien);
        String r=mvc.perform(req.contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return r.isBlank()?json.createObjectNode():json.readTree(r);
    }
    Map<String,Object> jornada(String fecha,String inicio,String fin){return Map.of("fecha",fecha,"horaInicio",inicio,"horaFin",fin,"curso","7° básico A","tipo","CLASE","actividades","Clase de ecuaciones de primer grado.","reflexion","Funcionó el trabajo en parejas.");}

    @Test void bitacoraRegistroVisadoYRetroalimentacion() throws Exception {
        String ayer=LocalDate.now().minusDays(1).toString();
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(ayer,"08:00","10:30"),201,alumno);
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(LocalDate.now().plusDays(3).toString(),"08:00","10:00"),400,alumno);
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(ayer,"10:00","09:00"),400,alumno);
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(ayer,"08:00","10:00"),404,otroAlumno);
        var lista=json.readTree(mvc.perform(get("/api/practicas/"+practica+"/bitacora").with(alumno)).andReturn().getResponse().getContentAsString());
        assertEquals(2.5,lista.get("resumen").get("horas").asDouble(),0.001);
        long id=lista.get("jornadas").get(0).get("id").asLong();
        // El colegio observa (con comentario obligatorio); el estudiante corrige y vuelve a quedar pendiente.
        enviar(post("/api/bitacora/"+id+"/visado"),Map.of("decision","OBSERVADA"),400,colegioUser);
        enviar(post("/api/bitacora/"+id+"/visado"),Map.of("decision","OBSERVADA","comentario","Indica el contenido trabajado."),200,colegioUser);
        enviar(post("/api/bitacora/"+id+"/visado"),Map.of("decision","VISADA"),403,alumno);
        enviar(put("/api/bitacora/"+id),jornada(ayer,"08:00","10:30"),200,alumno);
        assertEquals("REGISTRADA",db.queryForObject("SELECT estado FROM bitacora_jornadas WHERE id=?",String.class,id));
        mvc.perform(get("/api/bitacora?pendientes=true").with(colegioUser)).andExpect(jsonPath("$.jornadas.length()").value(1));
        enviar(post("/api/bitacora/"+id+"/visado"),Map.of("decision","VISADA"),200,colegioUser);
        enviar(put("/api/bitacora/"+id),jornada(ayer,"08:00","11:00"),400,alumno);
        mvc.perform(delete("/api/bitacora/"+id).with(alumno)).andExpect(status().isBadRequest());
        // El profesor UBB deja retroalimentación; las horas visadas se suman.
        enviar(post("/api/bitacora/"+id+"/comentario"),Map.of("comentario","Buena reflexión."),200,profeUser);
        enviar(post("/api/bitacora/"+id+"/comentario"),Map.of("comentario","Hola"),403,colegioUser);
        mvc.perform(get("/api/practicas/"+practica+"/bitacora").with(profeUser)).andExpect(jsonPath("$.resumen.horas_visadas").value(2.5))
            .andExpect(jsonPath("$.jornadas[0].comentario_ubb").value("Buena reflexión."));
    }

    Map<String,Object> ficha(){return Map.of("curso","7° básico A","nivel","Educación básica","dias","Lunes y miércoles","horario","08:00 a 13:00","fechaInicio","2026-08-03","fechaTermino","2026-11-27","horasSemanales",10);}
    @Test void fichaInscripcionFlujoCompleto() throws Exception {
        enviar(post("/api/practicas/"+practica+"/ficha/enviar"),Map.of(),400,alumno);
        enviar(put("/api/practicas/"+practica+"/ficha"),ficha(),200,alumno);
        enviar(post("/api/practicas/"+practica+"/ficha/enviar"),Map.of(),200,alumno);
        enviar(put("/api/practicas/"+practica+"/ficha"),ficha(),400,alumno);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","DEVOLVER"),400,colegioUser);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","DEVOLVER","comentario","El horario no coincide."),200,colegioUser);
        mvc.perform(get("/api/practicas/"+practica+"/ficha").with(alumno)).andExpect(jsonPath("$.estado").value("DEVUELTA")).andExpect(jsonPath("$.comentario").value("El horario no coincide."));
        enviar(put("/api/practicas/"+practica+"/ficha"),ficha(),200,alumno);
        enviar(post("/api/practicas/"+practica+"/ficha/enviar"),Map.of(),200,alumno);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","APROBAR"),403,profeUser);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","APROBAR"),400,null);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","CONFIRMAR"),200,colegioUser);
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","APROBAR"),200,null);
        mvc.perform(get("/api/practicas/"+practica+"/ficha").with(profeUser)).andExpect(jsonPath("$.estado").value("APROBADA"));
        // Sin profesor de colegio, la ficha pasa directo a la coordinación.
        enviar(put("/api/practicas/"+practicaSinColegio+"/ficha"),ficha(),200,otroAlumno);
        enviar(post("/api/practicas/"+practicaSinColegio+"/ficha/enviar"),Map.of(),200,otroAlumno);
        assertEquals("CONFIRMADA",db.queryForObject("SELECT estado FROM fichas_inscripcion WHERE practica_id=?",String.class,practicaSinColegio));
    }

    @Test void rubricaMbeConNotaSugeridaYUsoComoCalificacion() throws Exception {
        mvc.perform(get("/api/rubrica").with(alumno)).andExpect(jsonPath("$.criterios.length()").value(12));
        var niveles=new HashMap<String,Integer>();for(var c:ReglasRubrica.CRITERIOS)niveles.put(c.codigo(),3);
        var incompleta=new HashMap<>(niveles);incompleta.remove("C2");
        enviar(post("/api/practicas/"+practica+"/rubricas"),Map.of("niveles",incompleta),400,colegioUser);
        enviar(post("/api/practicas/"+practica+"/rubricas"),Map.of("niveles",niveles),403,alumno);
        var r=enviar(post("/api/practicas/"+practica+"/rubricas"),Map.of("niveles",niveles,"comentario","Muy buen desempeño."),201,colegioUser);
        assertEquals(5.0,r.get("notaSugerida").asDouble(),0.001);
        var lista=json.readTree(mvc.perform(get("/api/practicas/"+practica+"/rubricas").with(profeUser)).andReturn().getResponse().getContentAsString());
        assertEquals(3.0,lista.get(0).get("dominios").get("A").asDouble(),0.001);
        long id=lista.get(0).get("id").asLong();
        enviar(post("/api/rubricas/"+id+"/usar-como-nota"),Map.of("ponderacion",30),403,colegioUser);
        enviar(post("/api/rubricas/"+id+"/usar-como-nota"),Map.of("ponderacion",30),200,profeUser);
        assertEquals(new java.math.BigDecimal("5.0"),db.queryForObject("SELECT nota FROM calificaciones WHERE practica_id=?",java.math.BigDecimal.class,practica));
    }

    @Test void notaSugeridaEsConversionLineal() {
        var todos=new HashMap<String,Integer>();
        for(int nivel=1;nivel<=4;nivel++){final int n=nivel;ReglasRubrica.CRITERIOS.forEach(c -> todos.put(c.codigo(),n));
            assertEquals(new java.math.BigDecimal(String.valueOf(2*n-1)+".0"),ReglasRubrica.notaSugerida(todos));}
    }

    @Test void cartaPresentacionEnPdf() throws Exception {
        var pdf=mvc.perform(get("/api/practicas/"+practica+"/carta.pdf").with(alumno)).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF",new String(pdf,0,4,StandardCharsets.US_ASCII));
        try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf)){
            String texto=new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertTrue(texto.contains("Ana Rojas Pérez") && texto.contains("Liceo de prueba") && texto.contains("Práctica pedagógica"),texto);
        }
        mvc.perform(get("/api/practicas/"+practicaSinColegio+"/carta.pdf")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/practicas/"+practica+"/carta.pdf").with(otroAlumno)).andExpect(status().isNotFound());
        mvc.perform(get("/api/practicas/"+practica+"/carta.pdf").with(colegioUser)).andExpect(status().isForbidden());
    }

    @Test void importarEstudiantesDesdeCsvYExcel() throws Exception {
        String csv="Identificador;Nombres;Apellidos;Correo\nNUEVO-1;María;López;maria@test.example\nNUEVO-2;Pedro;Díaz;correo-malo\nS3-1;Ana;Rojas;ana@test.example\n";
        var r=json.readTree(mvc.perform(multipart("/api/estudiantes/importar").file(new MockMultipartFile("archivo","curso.csv","text/csv",csv.getBytes(StandardCharsets.UTF_8)))
            .param("crearCuentas","true").param("claveInicial","InicioCurso2026")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,r.get("creados").asInt());assertEquals(1,r.get("cuentas").asInt());
        assertEquals(1,r.get("errores").size());assertEquals(3,r.get("errores").get(0).get("fila").asInt());
        assertEquals(1,r.get("omitidos").size());
        assertEquals("ESTUDIANTE",db.queryForObject("SELECT rol FROM usuarios WHERE correo='maria@test.example'",String.class));
        // Excel (.xlsx) con títulos en otro orden y sin crear cuentas.
        var salida=new java.io.ByteArrayOutputStream();
        try(var libro=new org.apache.poi.xssf.usermodel.XSSFWorkbook()){var h=libro.createSheet();var t=h.createRow(0);String[] tit={"Correo","Apellidos","Nombres","RUT"};for(int i=0;i<4;i++)t.createCell(i).setCellValue(tit[i]);
            var f=h.createRow(1);String[] d={"jose@test.example","Muñoz","José","NUEVO-3"};for(int i=0;i<4;i++)f.createCell(i).setCellValue(d[i]);libro.write(salida);}
        mvc.perform(multipart("/api/estudiantes/importar").file(new MockMultipartFile("archivo","curso.xlsx","application/octet-stream",salida.toByteArray())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.creados").value(1)).andExpect(jsonPath("$.cuentas").value(0));
        assertEquals("José",db.queryForObject("SELECT nombres FROM estudiantes WHERE identificador='NUEVO-3'",String.class));
        mvc.perform(multipart("/api/estudiantes/importar").file(new MockMultipartFile("archivo","a.csv","text/csv","Nombre;Correo\nA;b@c.cl".getBytes()))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/estudiantes/importar").file(new MockMultipartFile("archivo","curso.csv","text/csv",csv.getBytes())).param("crearCuentas","true").param("claveInicial","corta")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/estudiantes/importar").file(new MockMultipartFile("archivo","curso.csv","text/csv",csv.getBytes())).with(profeUser)).andExpect(status().isForbidden());
        mvc.perform(get("/api/estudiantes/plantilla.xlsx")).andExpect(status().isOk());
    }

    @Test void calendarioMuestraPlazosYJornadasSegunPermisos() throws Exception {
        LocalDate ayer=LocalDate.now().minusDays(1);
        db.update("UPDATE requisitos_practica SET fecha_limite=? WHERE practica_id=?",ayer.plusDays(5),practica);
        db.update("UPDATE requisitos_practica SET fecha_limite=? WHERE practica_id=?",ayer.plusDays(5),practicaSinColegio);
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(ayer.toString(),"08:00","10:00"),201,alumno);
        String q="/api/calendario?anio="+ayer.getYear()+"&mes="+ayer.getMonthValue();
        var propio=json.readTree(mvc.perform(get(q).with(alumno)).andReturn().getResponse().getContentAsString()).get("eventos");
        assertTrue(propio.size()>=1);
        for(var e:propio)assertEquals(practica,e.get("practica_id").asLong());
        if(ayer.getMonthValue()==ayer.plusDays(5).getMonthValue()) {
            boolean hayPlazo=false;for(var e:propio)hayPlazo|="PLAZO".equals(e.get("tipo").asText());assertTrue(hayPlazo);
        }
        mvc.perform(get("/api/calendario?anio=2026&mes=13").with(alumno)).andExpect(status().isBadRequest());
    }

    org.apache.poi.ss.usermodel.Sheet hojaExportada(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        var bytes=mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes)).getSheetAt(0);
    }
    @Test void exportarListasAExcelConBusquedaYPermisos() throws Exception {
        var est=hojaExportada(get("/api/exportar/estudiantes.xlsx"));
        assertEquals("Identificador",est.getRow(0).getCell(0).getStringCellValue());
        assertEquals(2,est.getLastRowNum());
        // La búsqueda se aplica sin importar mayúsculas ni tildes.
        var filtrada=hojaExportada(get("/api/exportar/estudiantes.xlsx").param("q","perez"));
        assertEquals(1,filtrada.getLastRowNum());
        assertEquals("Ana",filtrada.getRow(1).getCell(1).getStringCellValue());
        assertEquals(1,hojaExportada(get("/api/exportar/centros.xlsx")).getLastRowNum());
        assertTrue(hojaExportada(get("/api/exportar/usuarios.xlsx")).getLastRowNum()>=5);
        mvc.perform(get("/api/exportar/estudiantes.xlsx").with(profeUser)).andExpect(status().isForbidden());
        mvc.perform(get("/api/exportar/inventado.xlsx")).andExpect(status().isNotFound());
        // Bitácora: cada uno exporta lo que puede ver.
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(LocalDate.now().minusDays(1).toString(),"08:00","10:30"),201,alumno);
        var propia=hojaExportada(get("/api/exportar/bitacora.xlsx").with(alumno));
        assertEquals(1,propia.getLastRowNum());
        assertEquals(2.5,propia.getRow(1).getCell(5).getNumericCellValue(),0.001);
        assertEquals(0,hojaExportada(get("/api/exportar/bitacora.xlsx").with(otroAlumno)).getLastRowNum());
        assertEquals(1,hojaExportada(get("/api/exportar/bitacora.xlsx").with(colegioUser)).getLastRowNum());
    }

    List<String> avisos(RequestPostProcessor quien) throws Exception {
        var r=json.readTree(mvc.perform(get("/api/notificaciones").with(quien)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> textos=new ArrayList<>();r.get("avisos").forEach(a->textos.add(a.get("texto").asText()+"|"+a.get("pestana").asText()));return textos;
    }
    @Test void avisosDeLaCampanitaSegunElRol() throws Exception {
        // Estudiante: ficha pendiente y jornada observada.
        assertTrue(avisos(alumno).contains("Completa y envía tu ficha de inscripción|inscripcion"),avisos(alumno).toString());
        enviar(post("/api/practicas/"+practica+"/bitacora"),jornada(LocalDate.now().minusDays(1).toString(),"08:00","10:00"),201,alumno);
        // Profesor de colegio: jornada por visar.
        assertTrue(avisos(colegioUser).stream().anyMatch(a->a.startsWith("1 jornada por visar de Ana Rojas Pérez|bitacora")),avisos(colegioUser).toString());
        long id=db.queryForObject("SELECT id FROM bitacora_jornadas WHERE practica_id=?",Long.class,practica);
        enviar(post("/api/bitacora/"+id+"/visado"),Map.of("decision","OBSERVADA","comentario","Falta el curso."),200,colegioUser);
        assertTrue(avisos(alumno).stream().anyMatch(a->a.startsWith("Observaron tu jornada")),avisos(alumno).toString());
        assertTrue(avisos(colegioUser).stream().noneMatch(a->a.contains("por visar")));
        // Profesor UBB: entrega por revisar. Otro estudiante no ve avisos de esta práctica.
        mvc.perform(multipart("/api/practicas/"+practica+"/requisitos/"+db.queryForObject("SELECT MIN(id) FROM requisitos_practica WHERE practica_id=?",Long.class,practica)+"/documentos")
            .file(new MockMultipartFile("archivo","x.pdf","application/pdf","%PDF-1.4\nprueba\n%%EOF".getBytes())).with(alumno)).andExpect(status().isCreated());
        assertTrue(avisos(profeUser).contains("1 entrega por revisar de Ana Rojas Pérez|entregas"),avisos(profeUser).toString());
        assertTrue(avisos(otroAlumno).stream().noneMatch(a->a.contains("Observaron")));
        // Coordinación: ficha confirmada por aprobar.
        enviar(put("/api/practicas/"+practica+"/ficha"),ficha(),200,alumno);
        enviar(post("/api/practicas/"+practica+"/ficha/enviar"),Map.of(),200,alumno);
        assertTrue(avisos(colegioUser).contains("Confirma la ficha de inscripción de Ana Rojas Pérez|inscripcion"));
        enviar(post("/api/practicas/"+practica+"/ficha/decision"),Map.of("accion","CONFIRMAR"),200,colegioUser);
        assertTrue(avisos(user("coordinacion@test.example").roles("COORDINADOR")).contains("Aprueba la ficha de inscripción de Ana Rojas Pérez|inscripcion"));
    }

    @Test void coordinacionVePanelDelEstudianteEnSoloLectura() throws Exception {
        mvc.perform(get("/api/estudiantes/"+estudiante+"/panel")).andExpect(status().isOk())
            .andExpect(jsonPath("$.estudiante.nombres").value("Ana")).andExpect(jsonPath("$.practicas.length()").value(1));
        mvc.perform(get("/api/estudiantes/"+estudiante+"/panel").with(profeUser)).andExpect(status().isForbidden());
        mvc.perform(get("/api/estudiantes/999999/panel")).andExpect(status().isNotFound());
    }
}
