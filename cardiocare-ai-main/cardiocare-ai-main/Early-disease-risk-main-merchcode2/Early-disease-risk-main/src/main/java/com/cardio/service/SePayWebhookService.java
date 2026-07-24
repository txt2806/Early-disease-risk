package com.cardio.service;

import com.cardio.model.*;
import com.cardio.repository.*;
import com.cardio.controller.SePayWebhookController.SePayWebhookPayload;
import com.cardio.controller.SePayWebhookController.SePayIpnPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SePayWebhookService {

    private final InvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;

    @Async
    public void processWebhookAsync(SePayWebhookPayload payload) {
        log.info("SePay Webhook: Async processing started for transaction ID: {}", payload.getId());
        try {
            String codeText = payload.getCode();
            if (codeText == null || codeText.isBlank()) {
                codeText = payload.getContent();
            }
            if (codeText != null) {
                Pattern pattern = Pattern.compile("TT\\d+", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(codeText);
                if (matcher.find()) {
                    String refCode = matcher.group().toUpperCase();
                    Optional<Invoice> invOpt = invoiceRepository.findByReferenceCode(refCode);
                    if (invOpt.isPresent()) {
                        Invoice invoice = invOpt.get();
                        
                        Long transferAmount = payload.getTransferAmount();
                        invoice.setPaidAmount(invoice.getPaidAmount() + transferAmount);
                        invoice.setPaymentMethod("Chuyển khoản");
                        
                        if (invoice.getPaidAmount() >= invoice.getAmount()) {
                            invoice.setStatus("Paid");
                            invoice.setPaymentDate(LocalDateTime.now());
                            
                            Appointment app = invoice.getAppointment();
                            if (app != null && !"Cancelled".equalsIgnoreCase(app.getStatus())) {
                                app.setStatus("Confirmed");
                                appointmentRepository.save(app);
                            }
                        } else {
                            invoice.setStatus("PartiallyPaid");
                        }
                        
                        invoiceRepository.save(invoice);
                        log.info("SePay Webhook: Async updated invoice {} state to {} (PaidAmount: {})", 
                                invoice.getInvoiceId(), invoice.getStatus(), invoice.getPaidAmount());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing SePay Webhook asynchronously: ", e);
        }
    }

    @Async
    public void processIpnAsync(SePayIpnPayload payload) {
        log.info("SePay IPN: Async processing started for transaction ID: {}", payload.getTransaction().getTransaction_id());
        try {
            if (payload.getOrder() != null) {
                String codeText = payload.getOrder().getOrder_id();
                if (codeText == null || codeText.isBlank()) {
                    codeText = payload.getOrder().getOrder_description();
                }
                
                if (codeText != null) {
                    Pattern pattern = Pattern.compile("TT\\d+", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(codeText);
                    if (matcher.find()) {
                        String refCode = matcher.group().toUpperCase();
                        Optional<Invoice> invOpt = invoiceRepository.findByReferenceCode(refCode);
                        if (invOpt.isPresent()) {
                            Invoice invoice = invOpt.get();
                            
                            Double amtDouble = Double.parseDouble(payload.getOrder().getOrder_amount());
                            Long transferAmount = amtDouble.longValue();
                            
                            invoice.setPaidAmount(invoice.getPaidAmount() + transferAmount);
                            invoice.setPaymentMethod("Chuyển khoản (IPN)");
                            
                            if (invoice.getPaidAmount() >= invoice.getAmount()) {
                                invoice.setStatus("Paid");
                                invoice.setPaymentDate(LocalDateTime.now());
                                
                                Appointment app = invoice.getAppointment();
                                if (app != null && !"Cancelled".equalsIgnoreCase(app.getStatus())) {
                                    app.setStatus("Confirmed");
                                    appointmentRepository.save(app);
                                }
                            } else {
                                invoice.setStatus("PartiallyPaid");
                            }
                            
                            invoiceRepository.save(invoice);
                            log.info("SePay IPN: Async updated invoice {} state to {} (PaidAmount: {})", 
                                    invoice.getInvoiceId(), invoice.getStatus(), invoice.getPaidAmount());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing SePay IPN asynchronously: ", e);
        }
    }
}
