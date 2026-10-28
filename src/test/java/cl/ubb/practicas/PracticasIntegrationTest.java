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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
   for(String tabla:new String[]{"material_fragmentos","materiales","evaluaciones_colegio","revisiones","documentos","requisitos_practica","practicas","usuarios","estudiantes","centros"})db.update("DELETE FROM "+tabla);
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
  long cuenta(String correo,String rol,Long estudiante)throws Exception{
   var d=new java.util.HashMap<String,Object>();d.put("correo",correo);d.put("nombre","Persona de prueba");d.put("clave","ClavePrueba2026");d.put("rol",rol);d.put("estudianteId",estudiante);
   return crear("/usuarios",d,201).get("id").asLong();
  }
  @Test void s206EdicionConservaRelacionesYFiltrosCombinados()throws Exception{
   long p=practica("EDIT-1","INT1",2026),r=requisito(p);
   subir(p,r,"original.pdf",pdf(),201);
   long s=db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,p);
   long c=db.queryForObject("SELECT centro_id FROM practicas WHERE id=?",Long.class,p);
   mvc.perform(put("/api/estudiantes/"+s).contentType("application/json").content(json.writeValueAsBytes(Map.of("identificador","EDIT-1","nombres","Nombre nuevo","apellidos","Apellido","correo","nuevo@example.com")))).andExpect(status().isOk());
   mvc.perform(put("/api/centros/"+c).contentType("application/json").content("{\"nombre\":\"Centro actualizado\",\"comuna\":\"Chillán\"}")).andExpect(status().isOk());
   mvc.perform(get("/api/practicas").param("q","Nombre nuevo").param("anio","2026").param("semestre","2").param("asignaturaId",String.valueOf(asignatura("INT1")))).andExpect(jsonPath("$.length()").value(1));
   mvc.perform(get("/api/practicas").param("q","%' OR 1=1 --")).andExpect(jsonPath("$.length()").value(0));
   assertEquals(1,seguimiento(p).get("documentos").size());
  }
  @Test void s209PlantillasConservanInstantaneaYPlazos()throws Exception{
   var datos=Map.of("asignaturaId",asignatura("INI1"),"anio",2030,"semestre",2,"codigo","NUEVO","nombre","Requisito original","fechaLimite","2030-12-01");
   crear("/plantillas",datos,201);
   long p=practica("PLANT-1","INI1",2030);
   long plantilla=db.queryForObject("SELECT r.id FROM requisitos_plantilla r JOIN periodos pe ON pe.id=r.periodo_id WHERE pe.anio=2030 AND r.codigo='NUEVO'",Long.class);
   var edit=new java.util.HashMap<String,Object>(datos);edit.put("nombre","Requisito cambiado");
   mvc.perform(put("/api/plantillas/"+plantilla).contentType("application/json").content(json.writeValueAsBytes(edit))).andExpect(status().isOk());
   assertEquals("Requisito original",seguimiento(p).get("requisitos").get(0).get("nombre").asText());
   long r=requisito(p);
   mvc.perform(put("/api/requisitos/"+r+"/plazo").contentType("application/json").content("{\"fechaLimite\":\"2020-01-01\"}")).andExpect(status().isOk());
   assertTrue(seguimiento(p).get("requisitos").get(0).get("vencido").asBoolean());
   db.update("DELETE FROM requisitos_plantilla WHERE id=?",plantilla);
  }
  @Test void s204PanelEstudianteSoloMuestraLoPropio()throws Exception{
   long propia=practica("PORTAL-1","INI1",2026),ajena=practica("PORTAL-2","INI1",2026);
   long estudiante=db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,propia);
   cuenta("portal@test.example","ESTUDIANTE",estudiante);
   db.update("UPDATE requisitos_practica SET fecha_limite='2020-01-01' WHERE practica_id=?",propia);
   var alumno=user("portal@test.example").roles("ESTUDIANTE");
   var panel=json.readTree(mvc.perform(get("/api/mi-panel").with(alumno)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   assertEquals(1,panel.get("practicas").size());
   assertEquals(propia,panel.get("practicas").get(0).get("id").asLong());
   assertTrue(panel.get("plazos").get(0).get("vencido").asBoolean());
   mvc.perform(get("/api/practicas/"+ajena+"/seguimiento").with(alumno)).andExpect(status().isNotFound());
   mvc.perform(multipart("/api/practicas/"+ajena+"/requisitos/"+requisito(ajena)+"/documentos").file(new MockMultipartFile("archivo","x.pdf","application/pdf",pdf())).with(alumno)).andExpect(status().isNotFound());
   mvc.perform(multipart("/api/practicas/"+propia+"/requisitos/"+requisito(propia)+"/documentos").file(new MockMultipartFile("archivo","x.pdf","application/pdf",pdf())).with(alumno)).andExpect(status().isCreated());
   mvc.perform(get("/api/mi-panel")).andExpect(status().isForbidden());
  }
  JsonNode revisar(long documento,String decision,String comentario,int status)throws Exception{
   var body=new java.util.HashMap<String,Object>();body.put("decision",decision);body.put("comentario",comentario);
   return crear("/documentos/"+documento+"/revision",body,status);
  }
  JsonNode estadoRequisito(long p,long r)throws Exception{
   for(var x:seguimiento(p).get("requisitos"))if(x.get("id").asLong()==r)return x;
   throw new AssertionError("Requisito no encontrado");
  }
  @Test void s211RevisionCorreccionYNuevaVersionConservanHistorial()throws Exception{
   long p=practica("REV-1","INI1",2026),r=requisito(p);
   assertEquals("PENDIENTE",estadoRequisito(p,r).get("estado").asText());
   long v1=subir(p,r,"v1.pdf",pdf(),201).get("id").asLong();
   assertEquals("EN_REVISION",estadoRequisito(p,r).get("estado").asText());
   revisar(v1,"OBSERVADO","",400);
   revisar(v1,"OBSERVADO","Falta la bibliografía.",201);
   revisar(v1,"APROBADO","",400);
   assertEquals("OBSERVADO",estadoRequisito(p,r).get("estado").asText());
   long v2=subir(p,r,"v2.pdf",pdf(),201).get("id").asLong();
   revisar(v1,"APROBADO","",400);
   revisar(v2,"APROBADO","Correcto.",201);
   assertEquals("APROBADO",estadoRequisito(p,r).get("estado").asText());
   subir(p,r,"v3.pdf",pdf(),400);
   var docs=seguimiento(p).get("documentos");
   assertEquals(2,docs.size());
   assertEquals("Falta la bibliografía.",docs.get(1).get("comentario").asText());
   assertEquals(2,docs.get(0).get("version").asInt());
   long otra=practica("REV-2","INI1",2026);
   long d=subir(otra,requisito(otra),"x.pdf",pdf(),201).get("id").asLong();
   cuenta("revisor@test.example","PROFESOR_UBB",null);
   mvc.perform(post("/api/documentos/"+d+"/revision").with(user("revisor@test.example").roles("PROFESOR_UBB")).contentType("application/json").content("{\"decision\":\"APROBADO\"}")).andExpect(status().isNotFound());
  }
  @Test void s215IndicadoresDistinguenEstadosYAtrasos()throws Exception{
   long p=practica("IND-1","INT1",2026);
   long sinConfig=practica("IND-2","INI1",2031);
   var reqs=db.queryForList("SELECT id FROM requisitos_practica WHERE practica_id=? ORDER BY id",Long.class,p);
   assertEquals(2,reqs.size());
   long d1=subir(p,reqs.get(0),"a.pdf",pdf(),201).get("id").asLong();
   revisar(d1,"APROBADO","",201);
   db.update("UPDATE requisitos_practica SET fecha_limite='2020-01-01' WHERE id=?",reqs.get(1));
   var data=json.readTree(mvc.perform(get("/api/indicadores")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   var t=data.get("totales");
   assertEquals(2,t.get("practicas").asInt());
   assertEquals(1,t.get("aprobado").asInt());
   assertEquals(1,t.get("pendiente").asInt());
   assertEquals(1,t.get("vencidos").asInt());
   assertEquals(1,t.get("sin_configurar").asInt());
   assertEquals(0,t.get("completas").asInt());
   for(var fila:data.get("practicas")){
    if(fila.get("id").asLong()==p)assertEquals("CON_ATRASO",fila.get("situacion").asText());
    if(fila.get("id").asLong()==sinConfig)assertEquals("SIN_CONFIGURAR",fila.get("situacion").asText());
   }
   long d2=subir(p,reqs.get(1),"b.pdf",pdf(),201).get("id").asLong();
   var t2=json.readTree(mvc.perform(get("/api/indicadores")).andReturn().getResponse().getContentAsString()).get("totales");
   assertEquals(1,t2.get("en_revision").asInt());assertEquals(0,t2.get("vencidos").asInt());
   revisar(d2,"OBSERVADO","Corregir formato",201);
   var t3=json.readTree(mvc.perform(get("/api/indicadores")).andReturn().getResponse().getContentAsString()).get("totales");
   assertEquals(1,t3.get("observado").asInt());assertEquals(1,t3.get("vencidos").asInt());
   cuenta("sinpracticas@test.example","PROFESOR_UBB",null);
   mvc.perform(get("/api/indicadores").with(user("sinpracticas@test.example").roles("PROFESOR_UBB"))).andExpect(jsonPath("$.totales.practicas").value(0));
   cuenta("est-ind@test.example","ESTUDIANTE",db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,p));
   mvc.perform(get("/api/indicadores").with(user("est-ind@test.example").roles("ESTUDIANTE"))).andExpect(status().isForbidden());
  }
  static byte[] pdfConTexto(String... lineas)throws Exception{
   try(var doc=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){
    var pagina=new org.apache.pdfbox.pdmodel.PDPage();doc.addPage(pagina);
    try(var cs=new org.apache.pdfbox.pdmodel.PDPageContentStream(doc,pagina)){
     cs.beginText();cs.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),11);cs.newLineAtOffset(50,700);
     for(String l:lineas){cs.showText(l);cs.newLineAtOffset(0,-16);}
     cs.endText();
    }
    doc.save(out);return out.toByteArray();
   }
  }
  JsonNode cargarMaterial(String titulo,String nombre,byte[] bytes,String asignatura,int anio,int status)throws Exception{
   return json.readTree(mvc.perform(multipart("/api/materiales").file(new MockMultipartFile("archivo",nombre,"application/octet-stream",bytes))
    .param("titulo",titulo).param("asignaturaId",String.valueOf(asignatura(asignatura))).param("anio",String.valueOf(anio)).param("semestre","2"))
    .andExpect(status().is(status)).andReturn().getResponse().getContentAsString());
  }
  @Test void s216CargaProcesaPublicaYRetiraMaterial()throws Exception{
   byte[] pdf=pdfConTexto("Reglamento de practica pedagogica.","La asistencia minima al centro de practica es del 90 por ciento.","Las inasistencias se justifican con el profesor guia.");
   var r=cargarMaterial("Reglamento","reglamento.pdf",pdf,"INI1",2026,201);
   long id=r.get("id").asLong();assertTrue(r.get("fragmentos").asInt()>=1);
   var detalle=json.readTree(mvc.perform(get("/api/materiales/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   assertEquals("BORRADOR",detalle.get("estado").asText());
   assertTrue(detalle.get("vista_previa").asText().contains("asistencia minima"));
   mvc.perform(patch("/api/materiales/"+id+"/estado").contentType("application/json").content("{\"estado\":\"PUBLICADO\"}")).andExpect(status().isOk());
   mvc.perform(get("/api/materiales").param("asignaturaId",String.valueOf(asignatura("INI1")))).andExpect(jsonPath("$[0].estado").value("PUBLICADO"));
   mvc.perform(patch("/api/materiales/"+id+"/estado").contentType("application/json").content("{\"estado\":\"RETIRADO\"}")).andExpect(status().isOk());
   mvc.perform(patch("/api/materiales/"+id+"/estado").contentType("application/json").content("{\"estado\":\"BORRADO\"}")).andExpect(status().isBadRequest());
   mvc.perform(get("/api/materiales/"+id+"/descarga")).andExpect(status().isOk()).andExpect(content().bytes(pdf));
   cargarMaterial("Dañado","falso.pdf",pdf(),"INI1",2026,400);
   cargarMaterial("Vacío","vacio.txt","   \n ".getBytes(StandardCharsets.UTF_8),"INI1",2026,400);
   cargarMaterial("Ejecutable","programa.exe",pdf,"INI1",2026,400);
   var txt=cargarMaterial("Guía","guia.md","# Guía\n\nEl informe final se entrega en formato PDF con portada institucional.".getBytes(StandardCharsets.UTF_8),"INI1",2026,201);
   assertEquals(1,txt.get("fragmentos").asInt());
   long p=practica("MAT-1","INI1",2026);
   cuenta("lector@test.example","ESTUDIANTE",db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,p));
   mvc.perform(get("/api/materiales").with(user("lector@test.example").roles("ESTUDIANTE"))).andExpect(status().isForbidden());
   assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM materiales",Integer.class));
  }
  @Test void s214EvaluacionColegioSoloPracticantesAsignados()throws Exception{
   long p=practica("EVAL-1","INT1",2026),otra=practica("EVAL-2","INT1",2026);
   long ubb=cuenta("ubb@test.example","PROFESOR_UBB",null),colegio=cuenta("colegio@test.example","PROFESOR_COLEGIO",null);
   long estudiante=db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,p);
   cuenta("evaluado@test.example","ESTUDIANTE",estudiante);
   mvc.perform(put("/api/practicas/"+p+"/docentes").contentType("application/json").content("{\"profesorUbbId\":"+ubb+",\"profesorColegioId\":"+colegio+"}")).andExpect(status().isOk());
   var profe=user("colegio@test.example").roles("PROFESOR_COLEGIO");
   String evaluacion="{\"fortalezas\":\"Buena gestión del aula\",\"aspectosMejora\":\"Planificar cierres\",\"recomendaciones\":\"Usar material concreto\"}";
   mvc.perform(get("/api/practicas").with(profe)).andExpect(jsonPath("$.length()").value(1));
   mvc.perform(post("/api/practicas/"+p+"/evaluaciones").with(profe).contentType("application/json").content(evaluacion)).andExpect(status().isCreated());
   mvc.perform(post("/api/practicas/"+otra+"/evaluaciones").with(profe).contentType("application/json").content(evaluacion)).andExpect(status().isNotFound());
   mvc.perform(post("/api/practicas/"+p+"/evaluaciones").with(profe).contentType("application/json").content("{\"fortalezas\":\"\"}")).andExpect(status().isBadRequest());
   mvc.perform(post("/api/practicas/"+p+"/evaluaciones").with(user("ubb@test.example").roles("PROFESOR_UBB")).contentType("application/json").content(evaluacion)).andExpect(status().isForbidden());
   mvc.perform(get("/api/practicas/"+p+"/evaluaciones").with(user("ubb@test.example").roles("PROFESOR_UBB"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].fortalezas").value("Buena gestión del aula"));
   mvc.perform(get("/api/practicas/"+p+"/evaluaciones").with(user("evaluado@test.example").roles("ESTUDIANTE"))).andExpect(status().isForbidden());
  }
  @Test void s202AsignacionesPermisosYDesactivacion()throws Exception{
   long p=practica("PERM-1","INI1",2026);
   long estudiante=db.queryForObject("SELECT estudiante_id FROM practicas WHERE id=?",Long.class,p);
   long alumno=cuenta("alumno@test.example","ESTUDIANTE",estudiante);
   long profesor=cuenta("profesor@test.example","PROFESOR_UBB",null);
   mvc.perform(get("/api/practicas/"+p).with(user("profesor@test.example").roles("PROFESOR_UBB"))).andExpect(status().isNotFound());
   mvc.perform(put("/api/practicas/"+p+"/docentes").contentType("application/json").content("{\"profesorUbbId\":"+profesor+"}")).andExpect(status().isOk());
   mvc.perform(get("/api/practicas").with(user("profesor@test.example").roles("PROFESOR_UBB"))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
   mvc.perform(get("/api/usuarios").with(user("alumno@test.example").roles("ESTUDIANTE"))).andExpect(status().isForbidden());
   mvc.perform(patch("/api/usuarios/"+alumno+"/activo").contentType("application/json").content("{\"activo\":false}")).andExpect(status().isOk());
   mvc.perform(get("/api/practicas").with(user("alumno@test.example").roles("ESTUDIANTE"))).andExpect(status().isUnauthorized());
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM practicas WHERE id=?",Integer.class,p));
  }
}
