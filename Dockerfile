FROM pritunl/archlinux

ENV NVM_VERSION "v0.33.6"
ENV LUMO_VERSION "1.9.0"

RUN pacman -Syy --noconfirm git wget npm python2 make gcc bc && \
    pacman -Scc --noconfirm && \
    ln -s /usr/sbin/python2 /usr/sbin/python && \
    ln -s /usr/sbin/python2-config /usr/sbin/python-config && \
    npm install -g lumo-cljs@${LUMO_VERSION} --unsafe-perm && \
    wget -qO- "https://raw.githubusercontent.com/creationix/nvm/${NVM_VERSION}/install.sh" | bash && \
    mkdir /root/closh

WORKDIR /root/closh

COPY package.json .
COPY package-lock.json .

RUN . "$HOME/.nvm/nvm.sh" && \
    nvm install $(lumo -e '(println process.version)') && \
    npm install

COPY bin bin
COPY src src
COPY test test

CMD ["/usr/sbin/npm", "run", "start"]
