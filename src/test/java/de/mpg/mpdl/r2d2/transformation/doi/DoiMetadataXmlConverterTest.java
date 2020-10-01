package de.mpg.mpdl.r2d2.transformation.doi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import de.mpg.mpdl.r2d2.transformation.doi.model.DoiMetadata;
import de.mpg.mpdl.r2d2.util.testdata.DoiMetadataBuilder;

public class DoiMetadataXmlConverterTest {

  private DoiMetadataXmlConverter doiMetadataXmlConverter = new DoiMetadataXmlConverter();

  @Test
  public void testConvertToXML() throws JAXBException, SAXException {
    //Given
    DoiMetadata doiMetadata = new DoiMetadataBuilder().setTitle("TestTitle").setCreator("Creator Name", "Given Name", "Family Name")
        .setPublicationYear(2020).create();

    //When
    String metadataXml = doiMetadataXmlConverter.convertToXML(doiMetadata);

    //Then
    assertThat(metadataXml).isNotNull();
    //TODO Assert the values of the unmarshalled DoiMetadata
    this.unmarshalDoiMetadataXml(metadataXml);
  }

  private DoiMetadata unmarshalDoiMetadataXml(String metadataXml) throws JAXBException, SAXException {
    JAXBContext context = JAXBContext.newInstance(DoiMetadata.class);
    Unmarshaller unmarshaller = context.createUnmarshaller();

    //TODO: validate the xml with the Datacite schema?
    //    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    //    File xsdFile = new File(this.getClass().getClassLoader().getResource("metadata.xsd").getFile());
    //    Schema schema = schemaFactory.newSchema(xsdFile);
    //    unmarshaller.setSchema(schema);

    StringReader xmlStringReader = new StringReader(metadataXml);
    DoiMetadata doiMetadata = (DoiMetadata) unmarshaller.unmarshal(xmlStringReader);

    return doiMetadata;
  }

}
