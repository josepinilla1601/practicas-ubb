package cl.ubb.practicas;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Pruebas reales contra MySQL, exclusivamente en una base terminada en _test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username="coordinacion@test.example",roles="COORDINADOR")
class PracticasIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired WebApplicationContext context;
  @Autowired PasswordEncoder encoder;
 @BeforeEach void limpiarSoloBaseTest(){
  assertTrue(db.queryForObject("SELECT DATABASE()",String.class).endsWith("_test"),"Las pruebas necesitan una base exclusiva terminada en _test.");
   for(String tabla:new String[]{"documentos","requisitos_practica","practicas","usuarios","estudiantes","centros"})db.update("DELETE FROM "+tabla);
   db.update("INSERT INTO usuarios(correo,nombre,clave_hash,rol) VALUES('coordinacion@test.example','Pruebas','no-utilizada','COORDINADOR')");
   mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).defaultRequest(get("/").with(csrf())).build();
 }
 JsonNode crear(String ruta,Object body,int status)throws Exception{
  return json.readTree(mvc.perform(post("/api"+ruta).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
 }
 long alumno(String id)throws Exception{return crear("/estudiantes",Map.of("identificador",id,"nombres","Ana","apellidos","Ejemplo","correo","ana@example.com"),201).get("id").asLong();}
 long centro()throws Exception{return crear("/centros",Map.of("nombre","Colegio de prueba","comuna","Chillán"),201).get("id").asLong();}
 long asignatura(String codigo){return db.queryForObject("SELECT id FROM asignaturas WHERE codigo=?",Long.class,codigo);}
 Map<String,Object> datos(long estudiante,String codigo,int anio,Long centro){var m=new java.util.HashMap<String,Object>();m.put("estudianteId",estudiante);m.put("asignaturaId",asignatura(codigo));m.put("anio",anio);m.put("semestre",2);m.put("profesorResponsable","Profesor de prueba");m.put("centroId",centro);return m;}
 long practica(String id,String codigo,int anio)throws Exception{return crear("/practicas",datos(alumno(id),codigo,anio,codigo.equals("INI1")?null:centro()),201).get("id").asLong();}
 JsonNode seguimiento(long p)throws Exception{return json.readTree(mvc.perform(get("/api/practicas/"+p+"/seguimiento")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 long requisito(long p){return db.queryForObject("SELECT MIN(id) FROM requisitos_practica WHERE practica_id=?",Long.class,p);}
 byte[] pdf(){return "%PDF-1.4\nDocumento ficticio para probar transporte binario\n%%EOF".getBytes(StandardCharsets.UTF_8);}
 JsonNode subir(long p,long r,String name,byte[] bytes,int status)throws Exception{
  return json.readTree(mvc.perform(multipart("/api/practicas/"+p+"/requisitos/"+r+"/documentos").file(new MockMultipartFile("archivo",name,"application/octet-stream",bytes))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
 }
 @Test void hu01RegistroListadoYDuplicado()throws Exception{
  long id=alumno(" demo-001 ");
  mvc.perform(get("/api/estudiantes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].identificador").value("DEMO-001"));
  crear("/estudiantes",Map.of("identificador","DEMO-001","nombres","Otro","apellidos","Ejemplo","correo","otro@example.com"),409);
  mvc.perform(get("/api/estudiantes/"+id)).andExpect(status().isOk());
 }
 @Test void hu01RechazaDatosIncompletosOCorreoInvalido()throws Exception{
  crear("/estudiantes",Map.of("identificador","x"),400);
  crear("/estudiantes",Map.of("identificador","x","nombres","Ana","apellidos","Ejemplo","correo","correo-invalido"),400);
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM estudiantes",Integer.class));
 }
 @Test void hu02RegistraCentroYValidaObligatorios()throws Exception{
  centro();mvc.perform(get("/api/centros")).andExpect(jsonPath("$[0].comuna").value("Chillán"));
  crear("/centros",Map.of("nombre","Colegio sin comuna"),400);
 }
 @Test void hu03AsociacionesYDuplicado()throws Exception{
  long s=alumno("P-1");var d=datos(s,"INT1",2026,null);crear("/practicas",d,400);
  d.put("centroId",centro());crear("/practicas",d,201);crear("/practicas",d,409);
  d.put("estudianteId",999999);crear("/practicas",d,404);
  long p=practica("P-2","INI1",2026);assertEquals(1,seguimiento(p).get("practica").get("requeridos").asInt());
 }
 @Test void hu04CargaDescargaExactaYHu05ConteoPorRequisito()throws Exception{
  long p=practica("DOC-1","INT1",2026),r=requisito(p);
  assertEquals(0,seguimiento(p).get("practica").get("recibidos").asInt());
  long id=subir(p,r,"evidencia.pdf",pdf(),201).get("id").asLong();
  mvc.perform(get("/api/documentos/"+id+"/descarga")).andExpect(status().isOk()).andExpect(content().bytes(pdf())).andExpect(header().string("X-Content-Type-Options","nosniff"));
  subir(p,r,"version2.pdf",pdf(),201);
  JsonNode detalle=seguimiento(p);assertEquals(2,detalle.get("documentos").size());assertEquals(1,detalle.get("practica").get("recibidos").asInt());assertEquals(2,detalle.get("practica").get("requeridos").asInt());
  long otro=db.queryForObject("SELECT MAX(id) FROM requisitos_practica WHERE practica_id=?",Long.class,p);
  subir(p,otro,"otro.pdf",pdf(),201);assertEquals(2,seguimiento(p).get("practica").get("recibidos").asInt());
 }
 @Test void hu04RechazaRequisitoAjenoYArchivoInvalido()throws Exception{
  long a=practica("A","INI1",2026),b=practica("B","INI1",2026),r=requisito(a);
  subir(b,r,"archivo.pdf",pdf(),404);subir(a,r,"archivo.pdf",new byte[0],400);subir(a,r,"archivo.exe",pdf(),400);subir(a,r,"archivo.pdf","No es PDF".getBytes(),400);
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM documentos",Integer.class));
 }
  @Test void hu05PeriodoSinConfiguracionNoSeConsideraCompleto()throws Exception{
  long p=practica("SIN-CONFIG","INI1",2027);var d=seguimiento(p);
   assertFalse(d.get("configurado").asBoolean());assertTrue(d.get("requisitos").isEmpty());assertEquals(0,d.get("practica").get("requeridos").asInt());
  }
  @Test @WithAnonymousUser void s201SesionLoginCambioClaveYLogout()throws Exception{
   org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
   db.update("UPDATE usuarios SET clave_hash=?",encoder.encode("InicialSegura2026"));
   mvc.perform(get("/api/estudiantes")).andExpect(status().isUnauthorized());
   mvc.perform(post("/api/login").param("username","coordinacion@test.example").param("password","incorrecta")).andExpect(status().isUnauthorized());
   var resultado=mvc.perform(post("/api/login").param("username","coordinacion@test.example").param("password","InicialSegura2026")).andExpect(status().isOk()).andReturn();
   var sesion=(MockHttpSession)resultado.getRequest().getSession(false);
   assertNotNull(sesion);
   mvc.perform(get("/api/sesion").session(sesion)).andExpect(jsonPath("$.usuario.rol").value("COORDINADOR"));
   mvc.perform(post("/api/cuenta/clave").session(sesion).contentType("application/json").content("{\"actual\":\"InicialSegura2026\",\"nueva\":\"NuevaClaveSegura2026\"}")).andExpect(status().isOk());
   mvc.perform(post("/api/logout").session(sesion)).andExpect(status().isOk());
   mvc.perform(post("/api/login").param("username","coordinacion@test.example").param("password","InicialSegura2026")).andExpect(status().isUnauthorized());
   mvc.perform(post("/api/login").param("username","coordinacion@test.example").param("password","NuevaClaveSegura2026")).andExpect(status().isOk());
  }
  @Test void s201RechazaCsrfInvalido()throws Exception{
   mvc.perform(post("/api/estudiantes").with(csrf().useInvalidToken()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  }
}
