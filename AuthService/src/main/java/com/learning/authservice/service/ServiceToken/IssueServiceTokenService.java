package com.learning.authservice.service.ServiceToken;

import com.learning.authservice.dto.ServiceTokenRequest;
import com.learning.authservice.dto.ServiceTokenResponse;

public interface IssueServiceTokenService {
    ServiceTokenResponse issueServiceToken(ServiceTokenRequest request);
}