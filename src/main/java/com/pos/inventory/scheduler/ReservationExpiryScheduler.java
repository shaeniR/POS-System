package com.pos.inventory.scheduler;

import com.pos.inventory.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically sweeps for reservations past their expiry time and releases their stock.
 * Each reservation is expired in its own transaction, so one failure cannot roll back or block the others.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${pos.reservation.expiry-check-interval-ms:5000}")
    public void scheduledRelease() {
        releaseExpiredReservations();
    }

    /**
     * @return the number of reservations expired by this run
     */
    public int releaseExpiredReservations() {
        List<Long> expiredOrderIds = reservationService.findExpiredReservationIds();
        int released = 0;

        for (Long orderId : expiredOrderIds) {
            try {
                if (reservationService.expireReservation(orderId)) {
                    released++;
                }
            } catch (Exception e) {
                log.error("Failed to expire reservation for order ID {}; will retry on next run", orderId, e);
            }
        }

        if (released > 0) {
            log.info("Released {} expired reservation(s)", released);
        }
        return released;
    }
}
