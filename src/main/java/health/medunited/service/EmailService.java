package health.medunited.service;

import javax.activation.DataHandler;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.mail.internet.MimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import health.medunited.model.LetterRequest;
import health.medunited.model.MailSubjects;
import health.medunited.model.PharmacyRequest;
import health.medunited.model.PowershellRequest;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@ApplicationScoped
public class EmailService {

    private static final Logger log = LogManager.getLogger(EmailService.class);

    private static final String EMAILTEXT =
            "Bitte führen Sie dieses Powershell-Skript mit folgendem Befehl aus: PowerShell.exe -ExecutionPolicy Bypass -File t2med.ps1";

    @Inject
    PdfService pdfService;

    @Inject
    Mailer mailer;

    public void sendToDoctor(LetterRequest letterRequest) {

        String toKimAddress = letterRequest.getContactEmail();

        try {

            MimeBodyPart pdf = new MimeBodyPart();
            ByteArrayDataSource ds = new ByteArrayDataSource(pdfService.generatePdfFile(letterRequest.getDatamatrices()).readAllBytes(),
                    "application/pdf");
            pdf.setDataHandler(new DataHandler(ds));

            Mail email = Mail.withText(toKimAddress, MailSubjects.EARZTBRIEF.value, letterRequest.getContactMessage());
            List<String> allXMLs = letterRequest.getAttachment();
            for (int i = 0; i < allXMLs.size(); i++) {
                String xml = allXMLs.get(i);
                MimeBodyPart attachment = new MimeBodyPart();
                attachment.setContent(xml, "text/xml");
                email = email.addAttachment("xmlattach" + i, attachment.getInputStream().readAllBytes(), "text/xml");
            }
            mailer.send(email.addAttachment("pdfattach", pdf.getInputStream().readAllBytes(), "application/pdf"));

            log.info("E-Mail sent successfully to: {}", toKimAddress);
        } catch (Exception e) {
            log.error("Error during sending E-Prescription: {}", e.getMessage());
        }
    }

    public void notify(PharmacyRequest pharmacyRequest) {

        String toKimAddress = pharmacyRequest.getPharmacyEmail();

        mailer.send(Mail.withText(toKimAddress, MailSubjects.PHARMACYNOTIFIER.value, buildEmailText(pharmacyRequest)));
    }

    public void sendPowershellScript(PowershellRequest powershellRequest) {

        String toKimAddress = powershellRequest.getDoctorEmail();

        try {
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.attachFile(powershellRequest.getFile());

            mailer.send(Mail.withText(toKimAddress, MailSubjects.T2MED.value, EMAILTEXT)
                    .addAttachment("t2med.txt",
                            attachment.getInputStream().readAllBytes(), "text/plain")
            );
            log.info("E-Mail sent successfully to: {}", toKimAddress);
        } catch (Exception e) {
            log.error("Error during sending E-Prescription: {}", e.getMessage());
        }
    }

    private String buildEmailText(PharmacyRequest pharmacyRequest) {
        int numberOfMedicationStatementsRequestedToPharmacy = pharmacyRequest.getPatients().size();
        String text = "Patientenname, Name des Arztes, Medikamente pzn, Status, Anforderungsdatum " + "\n";
        for (int i = 0; i < numberOfMedicationStatementsRequestedToPharmacy; i++) {
            String patient = pharmacyRequest.getPatients().get(i);
            String doctor = pharmacyRequest.getDoctors().get(i);
            String pzn = pharmacyRequest.getPzns().get(i);
            text += patient + ", " + doctor + ", " + pzn + ", " + pharmacyRequest.getStatus() + ", " + pharmacyRequest.getRequestDate() + "\n";
        }
        return text;
    }

}
