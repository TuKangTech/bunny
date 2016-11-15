#!/bin/bash
virtualenv env/testenv
source env/testenv/bin/activate
pip install -e git+https://github.com/common-workflow-language/cwltest.git@master#egg=cwltest
cwltest --test conformance_test_draft-2.yaml --tool ~/build/rabix/bunny/rabix-backend-local/target/rabix-backend-local-0.6.1-SNAPSHOT-id3/rabix -j 4
