package com.company.payment.payment.mapper;

import com.company.payment.payment.model.Amount;
import com.company.payment.payment.model.BillingInfo;
import com.company.payment.payment.model.CardInfo;
import com.company.payment.payment.model.DeviceInfo;
import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.common.util.Amounts;
import com.company.payment.ni.dto.NiAmount;
import com.company.payment.ni.dto.NiBillingAddress;
import com.company.payment.ni.dto.NiBrowserInfo;
import com.company.payment.ni.dto.NiCardRequest;
import com.company.payment.ni.dto.NiOrderRequest;
import com.company.payment.ni.dto.NiThreeDsRequest;
import com.company.payment.ni.dto.NiTokenCardRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** API request (payment/model) -> N-Genius DTOs. MapStruct generates the implementation at compile time. */
@Mapper
public interface PaymentNiMapper {

    /** Two-stage: action AUTH (authorise now, capture later). */
    @Mapping(target = "action", constant = "AUTH")
    @Mapping(target = "emailAddress", source = "billingInfo.email")
    @Mapping(target = "merchantOrderReference", source = "merchantOrderId")
    @Mapping(target = "billingAddress", source = "billingInfo")
    NiOrderRequest toOrderRequest(PaymentRequest request);

    @Mapping(target = "address1", source = "address")
    NiBillingAddress toBillingAddress(BillingInfo billingInfo);

    /** Major units -> minor units: AED 250.00 -> 25000. */
    default NiAmount toNiAmount(Amount amount) {
        return new NiAmount(amount.getCurrency(), Amounts.toMinorUnits(amount.getValue(), amount.getCurrency()));
    }

    /** CARD: pan, expiry, cvv, cardholderName. */
    NiCardRequest toCardRequest(CardInfo card);

    /** CARD_ON_FILE: the saved N-Genius token + expiry, cvv, cardholderName. */
    @Mapping(target = "cardToken", source = "token")
    NiTokenCardRequest toTokenCardRequest(CardInfo card);

    @Mapping(target = "deviceChannel", constant = "BRW")
    @Mapping(target = "threeDsCompInd", constant = "Y")
    @Mapping(target = "notificationUrl", source = "notificationUrl")
    @Mapping(target = "browserInfo", source = "deviceInfo")
    NiThreeDsRequest toThreeDsRequest(DeviceInfo deviceInfo, String notificationUrl);

    @Mapping(target = "browserAcceptHeader", source = "acceptHeader")
    @Mapping(target = "browserJavaEnabled", source = "javaEnabled")
    @Mapping(target = "browserLanguage", source = "language")
    @Mapping(target = "browserTz", source = "timeZoneOffset")
    @Mapping(target = "browserUserAgent", source = "userAgent")
    @Mapping(target = "browserColorDepth", source = "colorDepth")
    @Mapping(target = "browserScreenHeight", source = "screenHeight")
    @Mapping(target = "browserScreenWidth", source = "screenWidth")
    @Mapping(target = "browserJavascriptEnabled", source = "javascriptEnabled")
    @Mapping(target = "browserIp", source = "ip")
    @Mapping(target = "challengeWindowSize", source = "challengeWindowSize", defaultValue = "05")
    NiBrowserInfo toBrowserInfo(DeviceInfo deviceInfo);
}
