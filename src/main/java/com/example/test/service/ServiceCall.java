package com.example.test.service;

import com.example.test.dto.DoTransDto;
import com.example.test.model.User;

/** Contract from the supplied starter project; implemented by {@link WalletService}. */
public interface ServiceCall {

    void createUserAndAccount(User user);

    void doIntraTransfer(DoTransDto request);
}
