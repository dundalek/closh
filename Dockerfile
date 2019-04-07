FROM archlinux/base

ENV NVM_VERSION "v0.33.6"
ENV LUMO_VERSION "1.9.0-alpha"

RUN mkdir /root/closh
WORKDIR /root/closh

COPY scripts/ci_linux .
RUN ./ci_linux

COPY package.json .
COPY package-lock.json .

RUN npm install

COPY bin bin
COPY src src
COPY test test
COPY scripts scripts

CMD ["/usr/sbin/npm", "run", "start"]
